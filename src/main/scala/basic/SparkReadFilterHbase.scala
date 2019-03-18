package basic

import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.filter.RandomRowFilter
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.protobuf.ProtobufUtil
import org.apache.hadoop.hbase.util.{Base64, Bytes}
import org.apache.spark.{SparkConf, SparkContext}

object SparkReadFilterHbase {
  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setMaster("local")
      .setAppName("sparkReadFilterHbase")
    val sc = new SparkContext(sparkConf)

    val tableName = "wanda:emr"
    val conf = HBaseConfiguration.create()
    //设置zooKeeper集群地址，也可以通过将hbase-site.xml导入classpath，但是建议在程序里这样设置
    conf.set("hbase.zookeeper.quorum", "hadoop01")
    //设置zookeeper连接端口，默认2181
    conf.set("hbase.zookeeper.property.clientPort", "2181")
    conf.set(TableInputFormat.INPUT_TABLE, tableName)
    conf.set(TableInputFormat.SCAN_COLUMNS, "specific:title")

    //设置scan对象，让filter生效
    //val scan = new Scan
    //scan.setFilter(new RandomRowFilter(0.5f))
    //conf.set(TableInputFormat.SCAN, Base64.encodeBytes(ProtobufUtil.toScan(scan).toByteArray))

    //设置扫描内容(住院病案文档)
    val startRowKey = "RN32_001"
    val endRowKey = "RN33_001"
    //设置scan对象，让filter生效
    val scan = new Scan(Bytes.toBytes(startRowKey),Bytes.toBytes(endRowKey))
    conf.set(TableInputFormat.SCAN, Base64.encodeBytes(ProtobufUtil.toScan(scan).toByteArray))

    //读取数据并转化成rdd
    val hBaseRDD = sc.newAPIHadoopRDD(conf, classOf[TableInputFormat],
      classOf[org.apache.hadoop.hbase.io.ImmutableBytesWritable],
      classOf[org.apache.hadoop.hbase.client.Result])

    hBaseRDD.foreach { case (_, result) =>
      //获取行键
      val key = Bytes.toString(result.getRow)
      //通过列族和列名获取列
      val emrName = Bytes.toString(result.getValue("specific".getBytes, "title".getBytes))
      if (key != null && emrName != null) {
        println(key + ":" + emrName)
      }
    }

    sc.stop()

  }

}
