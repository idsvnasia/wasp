package it.agilelab.bigdata.wasp.master.web.controllers

import java.time.Instant

import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import it.agilelab.bigdata.wasp.utils.JsonSupport

class EventController(events: EventsService)
    extends Directives
    with JsonSupport {

  val parseInstant: Unmarshaller[String, Instant] =
    Unmarshaller.identityUnmarshaller[String].map(x => Instant.parse(x))

  def getRoutes: Route = pretty(events(_))

  def pretty(subroute: Boolean => Route): Route =
    parameters('pretty.as[Boolean].?(false)) { pretty =>
      subroute(pretty)
    }

  def events(pretty: Boolean): Route = get {
    path("events") {
      parameter('search.as[String]) { search =>
        parameter('startTimestamp.as[Instant](parseInstant)) { startTimestamp =>
          parameter('endTimestamp.as[Instant](parseInstant)) { endTimestamp =>
            parameter('page.as[Int]) { page =>
              parameter('size.as[Int]) { size =>
                extractExecutionContext { implicit ec =>
                  complete {
                    import it.agilelab.bigdata.wasp.master.web.utils.JsonResultsHelper.AngularOkResponse
                    import spray.json._
                    events
                      .events(search, startTimestamp, endTimestamp, page, size)
                      .map { x =>
                        x.toJson
                          .toAngularOkResponse(pretty)
                      }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
