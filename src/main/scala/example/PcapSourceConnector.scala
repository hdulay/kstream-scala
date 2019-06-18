package example

import java.util

import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.common.requests.DescribeConfigsResponse.ConfigSource
import org.apache.kafka.connect.connector.Task
import org.apache.kafka.connect.source.SourceConnector

import scala.collection.JavaConverters._

object ZeekSourceConnector {
  val VERSION = "0.1"
  val configDef = new ConfigDef()
    .define("pcap file",
      ConfigDef.Type.STRING,
      ConfigDef.Importance.HIGH,
      "Source filename.")
}

class ZeekSourceConnector extends SourceConnector {
  var configuration: util.Map[String, String] = new util.HashMap[String, String]()
  override def start(props: util.Map[String, String]): Unit = this.configuration = props

  override def taskClass(): Class[_ <: Task] = classOf[ZeekConnectorTask]

  override def taskConfigs(maxTasks: Int): util.List[util.Map[String, String]] = {
    val config: util.Map[String, String] = new util.HashMap[String, String]()
    config.put(ConfigSource.TOPIC_CONFIG.toString,
      configuration.getOrDefault(ConfigSource.TOPIC_CONFIG.toString, "dns"))
    Array(config).toList.asJava
  }

  override def stop(): Unit = ???

  override def config(): ConfigDef = ZeekSourceConnector.configDef

  override def version(): String = ZeekSourceConnector.VERSION
}

class ZeekConnectorTask extends Task {
  override def version(): String = ZeekSourceConnector.VERSION

  override def start(props: util.Map[String, String]): Unit = ???

  override def stop(): Unit = ???
}
