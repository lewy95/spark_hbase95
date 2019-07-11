package basic

import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}

/**
  * 本地spark的数据仓库路径：在项目根目录下（不带hive的情况）
  */
object LocalSparkDW {

  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf()
      .setMaster("local")
      .setAppName("sparkWriteDW")

    //val sc = new SparkContext(sparkConf)

    val spark = SparkSession
      .builder()
      .config(sparkConf)
      .enableHiveSupport()
      .getOrCreate()

    spark.sql("drop database test_emr")
    spark.sql("create database if not EXISTS test_emr")
    spark.sql("use test_emr")

    spark.sql("create table if not exists t_item_index(" +
      "item_id int, " +
      "max_score int, " +
      "min_score int, " +
      "avg_score decimal(4,3), " +
      "fc_score decimal(4,3), " +
      "bzc_score decimal(4,3), " +
      "nandu decimal(4,3), " +
      "qufendu decimal(4,3)) " +
      "partitioned by(paper_code string,create_time string) " +
      "row format delimited fields terminated by ','")

    spark.sql("show tables").show()

    spark.stop()

  }

}
