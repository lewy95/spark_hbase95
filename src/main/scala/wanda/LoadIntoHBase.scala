package wanda

import org.apache.hadoop.hbase.{HBaseConfiguration, HColumnDescriptor, HTableDescriptor, TableName}
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable
import scala.xml.XML

object LoadIntoHBase {

  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setAppName("loadIntoHbase").setMaster("local")
    val sparkContext = new SparkContext(sparkConf)

    //获得xml文件
    val wdTuple = sparkContext.wholeTextFiles("d://mywanda/emr2")

    wdTuple.foreachPartition {
      item => {
        //创建hbaseConfig，配置hbase
        val hbaseConfig = HBaseConfiguration.create()
        //写入HBase，需要客户端连接zookeeper
        hbaseConfig.set("hbase.zookeeper.property.clientPort", "2181")
        hbaseConfig.set("hbase.zookeeper.quorum", "hadoop01")
        //创建一个连接
        val connection: Connection = ConnectionFactory.createConnection(hbaseConfig)
        val tableName = "wanda:emr"
        val columnFamilies: Array[String] = Array("basic", "specific")
        //判断表存不存在
        if (!isExistTable(tableName, connection)) {
          //不存在，创建一个表
          createTable(tableName, columnFamilies, connection)
        }
        val table: Table = connection.getTable(TableName.valueOf(tableName))

        item.foreach { wdInfo =>
          //解析xml文件的头部信息
          val hmap: mutable.HashMap[String, String] = getHeaderInfo(wdInfo._2)
          //for((key,value) <- hmap) println("key:" + key + ", value:" + value)

          val put: Put = new Put(Bytes.toBytes(hmap("idExtension")))

          put.addColumn(Bytes.toBytes(columnFamilies(0)), Bytes.toBytes("id"), Bytes.toBytes(hmap("id")))
          put.addColumn(Bytes.toBytes(columnFamilies(0)), Bytes.toBytes("typeCode"), Bytes.toBytes(hmap("codeType")))
          put.addColumn(Bytes.toBytes(columnFamilies(0)), Bytes.toBytes("effectiveTime"), Bytes.toBytes(hmap("effectiveTime")))
          put.addColumn(Bytes.toBytes(columnFamilies(0)), Bytes.toBytes("realmCode"), Bytes.toBytes(hmap("realmCode")))
          put.addColumn(Bytes.toBytes(columnFamilies(1)), Bytes.toBytes("title"), Bytes.toBytes(hmap("title")))
          put.addColumn(Bytes.toBytes(columnFamilies(1)), Bytes.toBytes("languageCode"), Bytes.toBytes(hmap("languageCode")))
          put.addColumn(Bytes.toBytes(columnFamilies(1)), Bytes.toBytes("content"), Bytes.toBytes(wdInfo._2))
          //将行键设置进表
          table.put(put)
        }
        table.close()
        connection.close()
      }
    }
    println("success to load")
    sparkContext.stop()
  }

  /**
    * 解析xml文档头部信息
    *
    * @param wdContext
    * @return
    */
  def getHeaderInfo(wdContext: String): mutable.HashMap[String, String] = {
    val headerMap: mutable.HashMap[String, String] = new mutable.HashMap[String, String]()

    val xemr: scala.xml.Elem = XML.loadString(wdContext)
    val idExtension = (xemr \ "id" \ "@extension").head.text
    val id = (xemr \ "id" \ "@root").head.text
    val codeType = (xemr \ "code" \ "@code").head.text
    val effectiveTime = (xemr \ "effectiveTime" \ "@value").head.text
    val title = (xemr \ "title").text
    val realmCode = (xemr \ "realmCode" \ "@code").head.text
    val languageCode = (xemr \ "languageCode" \ "@code").head.text

    headerMap.put("idExtension", idExtension)
    headerMap.put("id", id)
    headerMap.put("codeType", codeType)
    headerMap.put("effectiveTime", effectiveTime)
    headerMap.put("title", title)
    headerMap.put("realmCode", realmCode)
    headerMap.put("languageCode", languageCode)

    headerMap
  }

  /**
    * 判断表是否存在
    *
    * @param tableName
    * @param connection
    * @return
    */
  def isExistTable(tableName: String, connection: Connection): Boolean = {
    val admin: Admin = connection.getAdmin
    admin.tableExists(TableName.valueOf(tableName))
  }

  /**
    * 创建表
    *
    * @param tableName
    * @param columnFamily
    * @param connection
    */
  def createTable(tableName: String, columnFamily: Array[String], connection: Connection): Unit = {
    val admin: Admin = connection.getAdmin
    val tableDesc: HTableDescriptor = new HTableDescriptor(TableName.valueOf(tableName))
    for (cf <- columnFamily) {
      tableDesc.addFamily(new HColumnDescriptor(cf))
    }
    admin.createTable(tableDesc)
    println("创建表成功")

  }

  /**
    * 向表中添加数据
    *
    * @param tableName
    * @param row
    * @param columnFamily
    * @param column
    * @param value
    * @param connection
    */
  def addRow(tableName: String, row: String, columnFamily: String, column: String, value: String, connection: Connection): Unit = {
    val table: Table = connection.getTable(TableName.valueOf(tableName))
    val put: Put = new Put(Bytes.toBytes(row))
    put.addImmutable(Bytes.toBytes(columnFamily), Bytes.toBytes(column), Bytes.toBytes(value))
    table.put(put)
  }

}
