package example

import java.util

import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.common.requests.DescribeConfigsResponse.ConfigSource
import org.apache.kafka.connect.connector.Task
import org.apache.kafka.connect.source.{SourceConnector, SourceRecord, SourceTask}

import scala.collection.JavaConverters._

object PCAPSourceConnector {
  val VERSION = "0.1"
  val PTOPIC = "topic"
  val configDef = new ConfigDef()
    .define("pcap-file",
      ConfigDef.Type.STRING,
      ConfigDef.Importance.HIGH,
      "Source filename.")
}

class PCAPSourceConnector extends SourceConnector {
  var configuration: util.Map[String, String] = new util.HashMap[String, String]()
  override def start(props: util.Map[String, String]): Unit = this.configuration = props

  override def taskClass(): Class[_ <: Task] = classOf[PCAPConnectorTask]

  override def taskConfigs(maxTasks: Int): util.List[util.Map[String, String]] = {
    val config: util.Map[String, String] = new util.HashMap[String, String]()
    config.put(PCAPSourceConnector.PTOPIC,
      configuration.getOrDefault(PCAPSourceConnector.PTOPIC, "dns"))
    Array(config).toList.asJava
  }

  override def stop(): Unit = ???

  override def config(): ConfigDef = PCAPSourceConnector.configDef

  override def version(): String = PCAPSourceConnector.VERSION
}

class PCAPConnectorTask extends SourceTask {
  override def version(): String = PCAPSourceConnector.VERSION

  override def start(props: util.Map[String, String]): Unit = {

    val topic = props.get(PCAPSourceConnector.PTOPIC)
  }

  override def stop(): Unit = ???

  override def poll(): util.List[SourceRecord] = {
    ???
  }
}
