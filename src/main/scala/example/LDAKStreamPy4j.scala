package example

import java.time.Duration
import java.util
import java.util.Properties

import cc.mallet.types.{Instance, InstanceList}
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.Serdes._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.KStream
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}
import scopt.OptionParser
import py4j.GatewayServer


trait PyModel {
  def score(event: String): Double
}

object LDAKStreamPy4j extends App {

  val parser = new OptionParser[KStreamConfig]("lda kstream") {
    head("lda-kstream", "1.0")

    opt[String]('b', "broker"    ).action((x, c) => c.copy(broker     = x)).text("broker host and port")
    opt[String]('m', "model"     ).action((x, c) => c.copy(modelPath  = x)).text("the file name of the serialized model")
    opt[String]('n', "name"      ).action((x, c) => c.copy(name       = x)).text("the name of the app")
    opt[String]('s', "source"    ).action((x, c) => c.copy(source     = x)).text("source topic")
    opt[String]('x', "suspicious").action((x, c) => c.copy(suspicious = x)).text("suspicious topic")
    opt[String]('g', "good"      ).action((x, c) => c.copy(good       = x)).text("good topic")
  }

  val appConfig = parser.parse(args, KStreamConfig()).get

  import py4j.GatewayServer

  /**
    * Start the Py4J gateway server
    */
  GatewayServer.turnLoggingOff()
  val server: GatewayServer = new GatewayServer
  server.start()

  /**
    * Load the python model
    */
  val model: PyModel = server.getPythonServerEntryPoint(Array[Class[_]](classOf[PyModel])).asInstanceOf[PyModel]

  val config: Properties = {
    val p = new Properties()
    p.put(StreamsConfig.APPLICATION_ID_CONFIG, appConfig.name)
    val bootstrapServers = appConfig.broker
    p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    p
  }

  val builder = new StreamsBuilder()
  val textLines: KStream[String, String] = builder.stream[String, String](appConfig.source)

  val branches = textLines.branch(
    /**
      * branch(0) - if max probability is less than .3, then the event is suspicious
      */
    (k, v) =>  {
        model.score(v) < .3 // Threshold, if lower, then doesn't belong to any existing topic
    },

    /**
      * branch(1) - if the max probability is greater than .3, the event is good enough
      */
    (k, v) => true
  )

  /**
    * write the branch(0) into the suspicious topic
    */
  branches(0).to(appConfig.suspicious)
  /**
    * write the branch(1) into the good topic
    */
  branches(1).to(appConfig.good)

  val streams: KafkaStreams = new KafkaStreams(builder.build(), config)
  streams.start()

  sys.ShutdownHookThread {
    streams.close(Duration.ofSeconds(10))
    server.shutdown()
  }

}
