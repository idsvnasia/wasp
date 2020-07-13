package it.agile.bigdata.wasp.repository.postgres.bl

import it.agile.bigdata.wasp.repository.postgres.WaspPostgresDB
import it.agilelab.bigdata.wasp.repository.core.bl.{BatchJobBL, BatchSchedulersBL, ConfigManagerBL, DBConfigBL, DocumentBL, FactoryBL, FreeCodeBL, IndexBL, KeyValueBL, MlModelBL, PipegraphBL, ProcessGroupBL, ProducerBL, RawBL, SqlSourceBl, TopicBL, WebsocketBL}

class PostgresFactoryBL extends FactoryBL {

  override def getBatchJobBL: BatchJobBL = BatchJobBLImpl(WaspPostgresDB.getDB())

  override def getIndexBL: IndexBL = ???

  override def getPipegraphBL: PipegraphBL = ???

  override def getProducerBL: ProducerBL = ???

  override def getTopicBL: TopicBL = ???

  override def getMlModelBL: MlModelBL = ???

  override def getWebsocketBL: WebsocketBL = ???

  override def getBatchSchedulerBL: BatchSchedulersBL = ???

  override def getRawBL: RawBL = ???

  override def getKeyValueBL: KeyValueBL = ???

  override def getBatchSchedulersBL: BatchSchedulersBL = ???

  override def getDocumentBL: DocumentBL = ???

  override def getFreeCodeBL: FreeCodeBL =  FreeCodeBLImpl(WaspPostgresDB.getDB())

  override def getProcessGroupBL: ProcessGroupBL = ???

  override def getConfigManagerBL: ConfigManagerBL = ???

  override def getDBConfigBL: DBConfigBL = ???

  override def getSqlSourceBl: SqlSourceBl = ???
}
