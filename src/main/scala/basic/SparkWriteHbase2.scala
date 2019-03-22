package basic

import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Put, Table}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * 测试类：与项目无关
  * spark将数据写入hbase
  * 法二：连接到hbase client的方法，但必须这样写，hbase配置不能些在外层
  */
object SparkWriteHbase2 {
  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setMaster("local")
      .setAppName("sparkWriteHbase2")
    val sc = new SparkContext(sparkConf)

    //定义一些数据
    val data = sc.makeRDD(Array("row3,23,mbape,male,9000", "row4,27,neymaer,male,12000"))

    data.foreachPartition {
      iter => {
        //创建hbaseConfig
        val hbaseConfig = HBaseConfiguration.create()
        //写入HBase，需要客户端连接zookeeper
        hbaseConfig.set("hbase.zookeeper.property.clientPort", "2181")
        hbaseConfig.set("hbase.zookeeper.quorum", "hadoop01")
        val connection: Connection = ConnectionFactory.createConnection(hbaseConfig)
        val tableName = "wanda:player"
        //这里需要表提前创建成功
        val table: Table = connection.getTable(TableName.valueOf(tableName))
        iter.foreach { p =>
          val infos = p.split(",")
          val rowKey = infos(0)
          val age = infos(1)
          val name = infos(2)
          val gender = infos(3)
          val salary = infos(4)

          val put: Put = new Put(Bytes.toBytes(rowKey))
          put.addColumn(Bytes.toBytes("cf1"), Bytes.toBytes("name"), Bytes.toBytes(name))
          put.addColumn(Bytes.toBytes("cf1"), Bytes.toBytes("age"), Bytes.toBytes(age))
          put.addColumn(Bytes.toBytes("cf2"), Bytes.toBytes("gender"), Bytes.toBytes(gender))
          put.addColumn(Bytes.toBytes("cf2"), Bytes.toBytes("salary"), Bytes.toBytes(salary))
          //写入HBase中
          table.put(put)
        }
        table.close()
        connection.close()

      }
    }
    println("success to write")
    sc.stop()

  }

}
