package it.agile.bigdata.wasp.repository.postgres.bl

import it.agile.bigdata.wasp.repository.postgres.WaspPostgresDBImpl
import it.agile.bigdata.wasp.repository.postgres.utils.PostgresSuite
import it.agilelab.bigdata.wasp.models.SqlSourceModel
import it.agilelab.bigdata.wasp.models.configuration.{JdbcPartitioningInfo, PostgresDBConfigModel}

class SqlSourceBlImplTest extends PostgresSuite {

  val _config     = PostgresDBConfigModel(jdbcUrl, user, pass, driver, 10)
  val _pgDB       = new WaspPostgresDBImpl(_config)
  val sqlSourceBL = SqlSourceBLImpl(_pgDB)

  it should "test sqlSourceBL" in {

    sqlSourceBL.createTable()

    val info   = JdbcPartitioningInfo("pc", "lb", "ub")
    val model1 = SqlSourceModel("name", "conn name", "dbtable", Some(info), Some(1), Some(2))
    sqlSourceBL.persist(model1)

    val model2 = SqlSourceModel("name2", "conn name 2", "dbtable 2", Some(info), Some(1), Some(2))
    sqlSourceBL.persist(model2)

    sqlSourceBL.getByName(model1.name).get equals model1
    sqlSourceBL.getByName(model2.name).get equals model2
    sqlSourceBL.getByName(model1.name).map(_.name).get equals model1.name
    sqlSourceBL.getByName(model2.name).map(_.name).get equals model2.name

    sqlSourceBL.getByName("XXXX").isEmpty equals true

  }

  it should "test sqlSourceBL upsert" in {

    sqlSourceBL.createTable()
    val info   = JdbcPartitioningInfo("pc", "lb", "ub")
    val model1 = SqlSourceModel("name", "conn name", "dbtable", Some(info), Some(1), Some(2))
    sqlSourceBL.upsert(model1)

    sqlSourceBL.getByName(model1.name).get equals model1

    val model2 = SqlSourceModel("name", "conn name 2", "dbtable2", Some(info), Some(1), Some(2))
    sqlSourceBL.upsert(model2)

    sqlSourceBL.getByName(model1.name).get equals model2
    sqlSourceBL.getByName("XXXX").isEmpty equals true
    sqlSourceBL.getByName(model1.name).map(_.name).get equals model1.name
    sqlSourceBL.getByName(model2.name).map(_.dbtable).get equals model2.dbtable

  }

}
