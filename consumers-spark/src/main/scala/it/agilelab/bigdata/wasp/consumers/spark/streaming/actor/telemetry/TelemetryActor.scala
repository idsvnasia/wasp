package it.agilelab.bigdata.wasp.consumers.spark.streaming.actor.telemetry
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.{Properties, UUID}

import akka.actor.{Actor, Cancellable, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import it.agilelab.bigdata.wasp.consumers.spark.streaming.actor.etl.MonitorOutcome
import it.agilelab.bigdata.wasp.core.messages.TelemetryMessageJsonProtocol._
import it.agilelab.bigdata.wasp.core.messages.{TelemetryActorRedirection, TelemetryMessageSource, TelemetryMessageSourcesSummary}
import it.agilelab.bigdata.wasp.core.models.configuration.{KafkaEntryConfig, TinyKafkaConfig}
import it.agilelab.bigdata.wasp.core.{SystemPipegraphs, WaspSystem}
import org.apache.kafka.clients.producer.{KafkaProducer, Producer, ProducerRecord}
import org.apache.spark.sql.streaming.StreamingQueryProgress
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.parsing.json.{JSONFormat, JSONObject}
import scala.util.{Success, Try}

class TelemetryActor private (kafkaConnectionString: String, kafkaConfig: TinyKafkaConfig) extends Actor {


  private var writer: Producer[Array[Byte], Array[Byte]] = _
  private val mediator = DistributedPubSub(context.system).mediator
  private var actorRefMessagesRedirect = Actor.noSender
  private var connectionCancellable: Cancellable = _

  override def preStart(): Unit = {

    val props = new Properties()
    props.put("bootstrap.servers", kafkaConnectionString)
    props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
    props.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
    props.put("batch.size", "1048576")
    props.put("acks", "0")

    val notOverridableKeys = props.keySet.asScala

    kafkaConfig.others.filterNot(notOverridableKeys.contains(_)).foreach {
      case KafkaEntryConfig(key, value) => props.put(key, value)
    }

    writer = new KafkaProducer[Array[Byte], Array[Byte]](props)

    connectionCancellable = scheduleMessageToRedirectionActor()
  }


  override def postStop(): Unit = {
    writer.close()
  }


  override def receive: Receive = {
    case MonitorOutcome(_, _, Some(progress), _) => send(progress)

    //Saves the actorRef of the actor that will receive the telemetry messages
    case TelemetryActorRedirection(aRef) =>
      actorRefMessagesRedirect = aRef
      connectionCancellable.cancel()
    case _ =>

  }

  private def toMessage(message: Any): String = {
    message match {
      case data: Map[String, Any] => JSONObject(data).toString(JSONFormat.defaultFormatter)
      case data: TelemetryMessageSourcesSummary => data.toJson.toString()
    }
  }

  private def metric(header: Map[String, Any], metric: String, value:Double) =
    header + ("metric" -> metric) + ("value" -> value)


  private def isValidMetric(metric: Map[String,Any]) = {
    val value = metric("value").asInstanceOf[Double]

    !value.isNaN && !value.isInfinity
  }

  private def send(progress: StreamingQueryProgress) : Unit = {

    val messageId = progress.id.toString
    val sourceId = progress.name
    val timestamp = progress.timestamp

    val header = Map("messageId" -> messageId,
      "sourceId" -> sourceId,
      "timestamp" -> timestamp)

    val durationMs = progress.durationMs.asScala.map {
                      case (key, value) => metric(header, s"$key-durationMs", value.toDouble)
                     }.toSeq

    val metrics = durationMs :+
                  metric(header, "numberOfInputRows", progress.numInputRows) :+
                  metric(header, "inputRowsPerSecond", progress.inputRowsPerSecond) :+
                  metric(header, "processedRowsPerSecond", progress.processedRowsPerSecond)

    metrics.filter(isValidMetric)
           .map(toMessage)
           .foreach(send(UUID.randomUUID().toString, _))

    //Try needed because sometimes spark sends not correctly formatted JSONs
    Try {
      val sources: Seq[TelemetryMessageSource] = progress.sources.map(sourceProgress => {
        TelemetryMessageSource(
          messageId = messageId,
          sourceId = sourceId,
          timestamp = timestamp,
          description = sourceProgress.description,
          startOffset = sourceProgress.startOffset.parseJson.convertTo[Map[String, Map[String, Long]]],
          endOffset = sourceProgress.endOffset.parseJson.convertTo[Map[String, Map[String, Long]]]
        )
      }).toSeq

      sources

    } match {

      case Success(sources) =>
        val overallSources: TelemetryMessageSourcesSummary = TelemetryMessageSourcesSummary(sources)

        val streamingQueryProgressMessage: String = toMessage(overallSources)

        //Message sent to the Kafka telemetry topic
        send(UUID.randomUUID().toString, streamingQueryProgressMessage)

        if(actorRefMessagesRedirect != Actor.noSender) actorRefMessagesRedirect ! overallSources

      case _ =>
    }

  }


  private def send(key: String, message: String) : Unit = {

    val topic = SystemPipegraphs.telemetryTopic.name

    val record = new ProducerRecord[Array[Byte], Array[Byte]](
      topic,
      key.getBytes(StandardCharsets.UTF_8),
      message.getBytes(StandardCharsets.UTF_8)
    )

    writer.send(record)
  }

  private def scheduleMessageToRedirectionActor(): Cancellable = {

    implicit val ec: ExecutionContextExecutor = context.system.dispatcher

    val cancellable = context.system.scheduler.schedule(
      FiniteDuration(5, TimeUnit.SECONDS),
      FiniteDuration(5, TimeUnit.SECONDS),
      mediator,
      Publish(WaspSystem.telemetryPubSubTopic, TelemetryActorRedirection(self))
    )

    cancellable
  }
}

object TelemetryActor {

  def props(kafkaConnectionString:String , kafkaConfig: TinyKafkaConfig): Props =
    Props(new TelemetryActor(kafkaConnectionString,
                             kafkaConfig))

}