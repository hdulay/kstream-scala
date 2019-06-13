package example

import java.io.ObjectInputStream
import java.net.{HttpURLConnection, URL}
import java.util
import java.util.{Arrays, Properties}

import cc.mallet.types.{Instance, InstanceList}
import com.google.gson.Gson
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.Serdes._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.KStream
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class KStreamConfig(name: String = "lda kstream",
                         broker: String = "localhost:9092",
                         source : String = "dns",
                         suspicious : String = "suspicious",
                         good: String = "good",
                         threshold: Double = .3)

case class Message(value: String, key: String, score: Double, modelName: String)

object LDAKStream extends App {

  val parser = new scopt.OptionParser[KStreamConfig]("dns.trainer") {
    head("dns.trainer", "0.1")

    opt[Double]('t', "threshold")
      .optional()
      .action( (t, c) => c.copy(threshold = t) )
      .text("threshold value")

    opt[String]('b', "broker")
      .optional()
      .action( (x, c) => c.copy(broker = x) )
      .text("bootstrap server")

    opt[String]('n', "name")
      .optional()
      .action( (x, c) => c.copy(name = x) )
      .text("name of app")

    opt[String]('d', "datasource")
      .optional()
      .action( (x, c) => c.copy(source = x) )
      .text("source topic")

    opt[String]('s', "suspicious")
      .optional()
      .action( (x, c) => c.copy(suspicious = x) )
      .text("suspicious topic")

    opt[String]('g', "good")
      .optional()
      .action( (x, c) => c.copy(good = x) )
      .text("good topic")
  }

  val appConfig = parser.parse(args, KStreamConfig()).get
  print(appConfig)

  val config: Properties = {
    val p = new Properties()
    p.put(StreamsConfig.APPLICATION_ID_CONFIG, appConfig.name)
    val bootstrapServers = appConfig.broker
    p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    p
  }

  val builder = new StreamsBuilder()

  /**
    * Make sure you create the DNS topic first else it will not start.
    */
  val dnsLogs: KStream[String, String] = builder.stream[String, String](appConfig.source)
  val mc = ModelConsumer(appConfig.broker)

  val branches = dnsLogs
    .map((k,v) => {
      val model = mc.getModel()
      println(s"using model: ${model.name}")

      // Create a new instance named "test instance" with empty target and source fields.
      val event = new InstanceList(model.instances.getPipe)
      event.addThruPipe(new Instance(v, null, "instance", null))

      val inferencer = model.model.getInferencer
      val probabilities = inferencer.getSampledDistribution(event.get(0), 10, 1, 5)

      val stream = util.Arrays.stream(probabilities)
      val p = stream.max.getAsDouble // find the max probability. we don't care which topic it belongs

      (k,  Message(value=v, key=k, score=p, modelName=model.name))
    })
    .branch(
    /**
      * branch(0) - if max probability is less than .3, then the event is suspicious
      */
    (k, v) =>  v.score < appConfig.threshold, // Threshold, if lower, then doesn't belong to any existing topic

    /**
      * branch(1) - if the max probability is greater than .3, the event is good enough
      */
    (k, v) => true
  )

  /**
    * write the branch(0) into the suspicious topic
    */
  branches(0)
    .map((k,v) => {
      val gson = new Gson
      val json = gson.toJson(v)
      (k, json)
    })
    .to(appConfig.suspicious)
  /**
    * write the branch(1) into the good topic
    */
  branches(1)
    .map((k,v) => (k, v.value))
    .to(appConfig.good)

  val streams: KafkaStreams = new KafkaStreams(builder.build(), config)
  streams.start()

  sys.ShutdownHookThread {
    streams.close(java.time.Duration.ofSeconds(10))
  }

}

case class ModelConsumer(bootstrapServers : String = "localhost:9092") {

  val props = new Properties()
  props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
  props.put(ConsumerConfig.GROUP_ID_CONFIG, s"lda.model1")
  props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
  props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
  props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  import scala.concurrent.ExecutionContext.Implicits.global
  var model: Option[LDAModel] = Option.empty

  val modelConsumer = new KafkaConsumer[String, String](props)
  modelConsumer.subscribe(Arrays.asList("lda-model"))

  sys.ShutdownHookThread {
    modelConsumer.wakeup()
  }

  val future = Future {
    import scala.collection.JavaConverters._

    modelConsumer.subscribe(Arrays.asList("lda-model"))
    var closed = false
    val tp = new TopicPartition("lda-model", 0)
    while(!closed) {
      try {
        val modelRecords = modelConsumer.poll(java.time.Duration.ofSeconds(2))
        if (!modelRecords.isEmpty) {
          val record = modelRecords.iterator()
            .asScala
            .toStream
            .max(Ordering[Long].on[ConsumerRecord[String, String]](_.offset())) // get highest offset
          model = deserializeModel(record.value())
          println(s"new model : ${model.get.name}")
        }
        else if(model.isEmpty) {

          val position = modelConsumer.position(tp)
          modelConsumer.seek(tp, if(position < 1) position else position - 1)
        }
      }
      catch {
        case e: Throwable => {
          e.printStackTrace()
          modelConsumer.close()
          closed = true
        }
      }
    }
  }

  val firstModel = Future {
    println("waiting for model")
    while(model.isEmpty) {
      print('.')
      Thread.sleep(1000)
    }
    model
  }

  private def deserializeModel(path: String): Option[LDAModel] = {

    try {
      val url = new URL(path)
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      val is = connection.getInputStream
      val in = new ObjectInputStream(is)
      Some(in.readObject.asInstanceOf[LDAModel])
    }
    catch {
      case e: Throwable => {
        e.printStackTrace()
        Option.empty
      }
    }
  }

  def getModel(): LDAModel = {
    if(model.isEmpty) Await.result(firstModel, Duration.Inf).get
    else model.get
  }
}
