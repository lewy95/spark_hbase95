package basic

import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.{SparkConf, SparkContext}

/**
  * 测试类，与项目无关
  */
object SparkReadHbase {
  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setMaster("local")
      .setAppName("sparkReadHbase")
    val sc = new SparkContext(sparkConf)

    val tableName = "wanda:emr"
    val conf = HBaseConfiguration.create()
    //设置zooKeeper集群地址，也可以通过将hbase-site.xml导入classpath，但是建议在程序里这样设置
    conf.set("hbase.zookeeper.quorum", "hadoop01")
    //设置zookeeper连接端口，默认2181
    conf.set("hbase.zookeeper.property.clientPort", "2181")
    conf.set(TableInputFormat.INPUT_TABLE, tableName)
    conf.set(TableInputFormat.SCAN_COLUMNS, "specific:content")

    //读取数据并转化成rdd
    val hBaseRDD = sc.newAPIHadoopRDD(conf, classOf[TableInputFormat],
      classOf[org.apache.hadoop.hbase.io.ImmutableBytesWritable],
      classOf[org.apache.hadoop.hbase.client.Result])

    //val count = hBaseRDD.count()
    //println(count)

    hBaseRDD.foreach { case (_, result) =>
      //获取行键
      val key = Bytes.toString(result.getRow)
      //通过列族和列名获取列
      val emrName = Bytes.toString(result.getValue("specific".getBytes, "content".getBytes))
      if (key != null && emrName != null) {
        //println(key + ":" + emrName)
        println(emrName)
      }
    }

    sc.stop()

  }

}
