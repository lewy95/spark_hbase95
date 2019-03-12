package basic

import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.TableOutputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.mapreduce.Job
import org.apache.spark.{SparkConf, SparkContext}

/**
  * spark将数据写入hbase
  * 法一：mapreduce的方式
  */
object SparkWriteHbase1 {
  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setMaster("local")
      .setAppName("sparkWriteHbase1")
    val sc = new SparkContext(sparkConf)

    //hbase配置
    val resultConf = HBaseConfiguration.create()
    //设置zooKeeper集群地址，也可以通过将hbase-site.xml导入classpath，但是建议在程序里这样设置
    resultConf.set("hbase.zookeeper.quorum", "hadoop01")
    //设置zookeeper连接端口，默认2181
    resultConf.set("hbase.zookeeper.property.clientPort", "2181")
    //注意这里是output
    resultConf.set(TableOutputFormat.OUTPUT_TABLE, "wanda:player")

    //job配置
    val job = Job.getInstance(resultConf)
    job.setOutputKeyClass(classOf[ImmutableBytesWritable])
    job.setOutputValueClass(classOf[org.apache.hadoop.hbase.client.Result])
    job.setOutputFormatClass(classOf[TableOutputFormat[ImmutableBytesWritable]])

    //定义一些数据
    val data = sc.makeRDD(Array("row1,30,messi,male,10000", "row2,27,sarah,male,8000"))

    val hbaseRDD = data.map { line => {
      val infos = line.split(",")
      val rowKey = infos(0)
      val age = infos(1)
      val name = infos(2)
      val gender = infos(3)
      val salary = infos(4)

      val put = new Put(Bytes.toBytes(rowKey))
      put.addColumn(Bytes.toBytes("cf1"), Bytes.toBytes("name"), Bytes.toBytes(name))
      put.addColumn(Bytes.toBytes("cf1"), Bytes.toBytes("age"), Bytes.toBytes(age))
      put.addColumn(Bytes.toBytes("cf2"), Bytes.toBytes("gender"), Bytes.toBytes(gender))
      put.addColumn(Bytes.toBytes("cf2"), Bytes.toBytes("salary"), Bytes.toBytes(salary))

      (new ImmutableBytesWritable, put)
      }
    }
    //写入hbase
    hbaseRDD.saveAsNewAPIHadoopDataset(job.getConfiguration)
    println("success to write")

    sc.stop()

  }

}
