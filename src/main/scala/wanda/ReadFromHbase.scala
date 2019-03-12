package wanda

import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer
import scala.xml.XML

object ReadFromHbase {

  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setMaster("local")
      .setAppName("readFromHbase")
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

    //hBaseRDD.cache()

    //创建一个空的ArrayBuffer[(String,String)]，用来存放解析后的信息
    //val patAB: ArrayBuffer[(String,String)] = new ArrayBuffer[(String,String)]
    //放弃tuple，tuple最多只支持21个元素，若事实表字段太多不适合
    val patAB: ArrayBuffer[ArrayBuffer[String]] = new ArrayBuffer[ArrayBuffer[String]]

    hBaseRDD.foreach { case (_, result) =>
      //获取行键
      val key = Bytes.toString(result.getRow)
      //通过列族和列名获取列
      val emrName = Bytes.toString(result.getValue("specific".getBytes, "content".getBytes))
      if (key != null && emrName != null) {
        val patInfoArray = getPatientInfo(emrName)
        patAB += patInfoArray
      }
    }

    sc.stop()


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

    //主治医生id
    val mainDocId = ((pat \\"authenticator" \"assignedEntity" \"id")
      .filter(x=>(x \"@extension").text.charAt(2) =='6') \"@extension").text
    pab += mainDocId

    //入院时间
    val ruyuanTime = (pat \"componentOf" \"encompassingEncounter" \"effectiveTime" \"low" \"@value").text
    //以前六位作为id
    pab += ruyuanTime.substring(0,6)

    //入院科室
    val inClassId = (pat \"componentOf" \"encompassingEncounter" \"location" \"healthCareFacility"\"serviceProviderOrganization"
      \"asOrganizationPartOf" \"wholeOrganization" \"asOrganizationPartOf" \"wholeOrganization" \"id" \"@extension").head.text
    pab += inClassId

    //出院诊断疾病id
    val outSickId = ((pat \"component" \"structuredBody" \\"component" \"section" \\"entry"
      \"observation" \"code" \"qualifier" \"name").filter(_.attribute("displayName")
      .exists(_.text.equals("JB_001"))) \"@displayName").head.text
    pab += outSickId
  }

  case class Patient(idCard: String, genderId: String, ageId: String, addrId: String,
                     inTimeId: String, inClassId: String, mainDocId: String, outSickId: String)

}
