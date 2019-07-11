package wanda

import org.apache.spark.sql.SparkSession
import wanda.util.MysqlTrait

/**
  * 从mysql中导入维度表
  */
object LoadFromMysql extends MysqlTrait{

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("loadFromMysql")
      //.config("spark.sql.warehouse.dir", "file:///home/hadoop/lewy/spark-warehouse")
      .master("local")
      //.enableHiveSupport()
      .getOrCreate()

    //年龄维度
    val ageDF = spark.read.jdbc("jdbc:mysql://localhost:3306/emr","t_dimension_age", prop)
    //ageDF.show
    //插入到hive表，hive on spark集群环境下可用
    //ageDF.write.mode("append").format("hive").insertInto("t_dimension_age")

    //性别维度
    val genderDF = spark.read.jdbc("jdbc:mysql://localhost:3306/emr","t_dimension_gender", prop)
    //genderDF.write.mode("append").format("hive").insertInto("t_dimension_gender")

    //时间维度
    val timeDF = spark.read.jdbc("jdbc:mysql://localhost:3306/emr","t_dimension_time", prop)
    //timeDF.write.mode("append").format("hive").insertInto("t_dimension_time")

    //医生维度
    val doctorDF = spark.read.jdbc("jdbc:mysql://localhost:3306/emr","t_dimension_doctor", prop)
    //doctorDF.write.mode("append").format("hive").insertInto("t_dimension_doctor")

    //科室维度
    val classDF = spark.read.jdbc("jdbc:mysql://localhost:3306/emr","t_dimension_class", prop)
    //classDF.write.mode("append").format("hive").insertInto("t_dimension_class")

    //疾病维度
    val sickDF = spark.read.jdbc("jdbc:mysql://localhost:3306/emr","t_dimension_sick", prop)
    //sickDF.write.mode("append").format("hive").insertInto("t_dimension_sick")

    spark.stop()
  }
}
