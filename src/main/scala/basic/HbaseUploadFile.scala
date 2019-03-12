package basic

import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Put, Table}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.spark.{SparkConf, SparkContext}

object HbaseUploadFile {

  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setAppName("hbaseUploadFile").setMaster("local")
    val sparkContext = new SparkContext(sparkConf)

    val conf = HBaseConfiguration.create()
    //设置zooKeeper集群地址，也可以通过将hbase-site.xml导入classpath，但是建议在程序里这样设置
    conf.set("hbase.zookeeper.quorum", "hadoop01")
    //设置zookeeper连接端口，默认2181
    conf.set("hbase.zookeeper.property.clientPort", "2181")
    //conf.set(TableInputFormat.INPUT_TABLE, tableName)
    //conf.set(TableOutputFormat.OUTPUT_TABLE, "wanda:emr")

    val tableName = "wanda:emr"
    val connection: Connection = ConnectionFactory.createConnection(conf)
    val table: Table = connection.getTable(TableName.valueOf(tableName))

    //读取目录下所有文件
    val wdRdd = sparkContext.wholeTextFiles("d://mywanda")

    //文档内容
    val wdContent:String = wdRdd.first()._2

    //设置行键
    val put = new Put(Bytes.toBytes("row4"))
    put.addColumn(Bytes.toBytes("specific"),Bytes.toBytes("content"),Bytes.toBytes(wdContent))

    table.put(put)

    table.close()
    connection.close()

    sparkContext.stop()
  }
}
