package wanda

import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.protobuf.ProtobufUtil
import org.apache.hadoop.hbase.util.{Base64, Bytes}
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import wanda.util.MysqlTrait

import scala.collection.mutable.ArrayBuffer
import scala.xml.XML

case class Patient(idCard: String, genderId: String, ageId: String, addrId: String,
                   inTimeId: String, inClassId: String, mainDocId: String, outSickId: String)

/**
  * 解析患者必要信息，作为事实表字段
  */
object ReadFromHbase extends MysqlTrait{

  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setMaster("local")
      .setAppName("readFromHbase")
    val sparkContext = new SparkContext(sparkConf)

    val sparkSession = SparkSession.builder()
      .config(sparkConf)
      //.config("spark.sql.warehouse.dir", "file:///home/ofo/spark/spark-warehouse")
      .enableHiveSupport()
      .getOrCreate()
    //导入隐式转换
    import sparkSession.implicits._

    val tableName = "wanda:emr"
    val conf = HBaseConfiguration.create()
    //设置zooKeeper集群地址，也可以通过将hbase-site.xml导入classpath，但是建议在程序里这样设置
    conf.set("hbase.zookeeper.quorum", "hadoop01")
    //设置zookeeper连接端口，默认2181
    conf.set("hbase.zookeeper.property.clientPort", "2181")
    conf.set(TableInputFormat.INPUT_TABLE, tableName)

    //设置扫描内容(target：住院病案文档，rowkey range：RN32_xxx)
    val startRowKey = "RN32_001"
    val endRowKey = "RN33_001"
    //设置scan对象，让filter生效
    val scan = new Scan(Bytes.toBytes(startRowKey), Bytes.toBytes(endRowKey))
    conf.set(TableInputFormat.SCAN, Base64.encodeBytes(ProtobufUtil.toScan(scan).toByteArray))

    //读取数据并转化成rdd
    val hBaseRDD = sparkContext.newAPIHadoopRDD(conf, classOf[TableInputFormat],
      classOf[org.apache.hadoop.hbase.io.ImmutableBytesWritable],
      classOf[org.apache.hadoop.hbase.client.Result])

    //创建一个空的ArrayBuffer[(String,String)]，用来存放解析后的信息
    //val patAB: ArrayBuffer[(String,String)] = new ArrayBuffer[(String,String)]
    //放弃tuple，tuple最多只支持22个元素，若事实表字段太多不适合
    //采用ArrayBuffer
    //lazy val patAB: ArrayBuffer[ArrayBuffer[String]] = new ArrayBuffer[ArrayBuffer[String]]

    //获取患者信息的rdd
    val contentRDD = hBaseRDD.map { case (_, result) =>
      //解析文档中的患者信息
      getPatientInfo(Bytes.toString(result.getValue("specific".getBytes, "content".getBytes)))
    }

    //contentRDD.cache()

    //将患者信息rdd转化为事实表
    val patientFactDF = contentRDD.map(res => (res(0), res(1), res(2), res(3), res(4), res(5), res(6), res(7)))
      .toDF("idCard", "genderId", "ageId", "addr", "inTimeId", "inClassId", "mainDocId", "outSickId")

    patientFactDF.show()

    //直接将数据插入到mysql
    patientFactDF.write.mode("append").jdbc("jdbc:mysql://localhost/emr", "t_fact_patient", prop)

    /*
    //插入到hive表，hive on spark集群环境下可用
    sparkSession.sql("create database if not EXISTS wanda_emr")
    sparkSession.sql("use wanda_emr")
    //创建表
    sparkSession.sql("create table if not exists t_fact_patient(" +
      "idCard string, " +
      "genderId string, " +
      "ageId string, " +
      "addr string, " +
      "inTimeId string, " +
      "inClassId string, " +
      "mainDocId string, " +
      "outSickId string) " )

    patientFactDF.write.mode("append").format("hive").insertInto("t_fact_patient")
    */

    sparkContext.stop()

  }

  /**
    * 解析xml中患者主题信息
    *
    * @param wdContext
    * @return
    */
  def getPatientInfo(wdContext: String): ArrayBuffer[String] = {

    val pab = new ArrayBuffer[String]
    val pat: scala.xml.Elem = XML.loadString(wdContext)

    //身份证号
    val idCard = (pat \ "recordTarget" \ "patientRole" \ "patient" \ "id" \ "@extension").head.text
    pab += idCard.drop(2)

    //性别
    val genderId = (pat \ "recordTarget" \ "patientRole" \ "patient" \ "administrativeGenderCode" \ "@code").head.text
    pab += genderId

    //年龄段id  (模余10)
    val ageId = (pat \ "recordTarget" \ "patientRole" \ "patient" \ "age" \ "@value").head.text
    pab += (ageId.toInt / 10).toString

    //家庭地址
    val countyAddr = (pat \ "recordTarget" \ "patientRole" \ "addr" \ "county").head.text
    val cityAddr = (pat \ "recordTarget" \ "patientRole" \ "addr" \ "city").head.text
    val addr = cityAddr + " " + countyAddr
    pab += addr

    //入院时间
    val inTime = (pat \ "componentOf" \ "encompassingEncounter" \ "effectiveTime" \ "low" \ "@value").text
    //以前六位作为id
    pab += inTime.substring(0, 6)

    //入院科室
    val inClassId = (pat \ "componentOf" \ "encompassingEncounter" \ "location" \ "healthCareFacility" \ "serviceProviderOrganization"
      \ "asOrganizationPartOf" \ "wholeOrganization" \ "asOrganizationPartOf" \ "wholeOrganization" \ "id" \ "@extension").head.text
    pab += inClassId

    //主治医生id
    val mainDocId = ((pat \\ "authenticator" \ "assignedEntity" \ "id")
      .filter(x => (x \ "@extension").text.charAt(2) == '2') \ "@extension").text
    pab += mainDocId

    //出院诊断疾病id
    val outSickId = ((pat \ "component" \ "structuredBody" \\ "component" \ "section" \\ "entry"
      \ "observation" \ "code" \ "qualifier" \ "name").filter(_.attribute("displayName")
      .exists(_.text.substring(0, 2).equals("JB"))) \ "@displayName").head.text
    pab += outSickId

    pab
  }
}
