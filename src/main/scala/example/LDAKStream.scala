package example

import java.io.{ByteArrayInputStream, ObjectInputStream}
import java.util
import java.util.{Arrays, Collections, Properties}

import cc.mallet.types.{Instance, InstanceList}
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.Serdes._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.KStream
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class KStreamConfig(modelPath: String = "./dns.lda.model",
                         name: String = "lda kstream",
                         broker: String = "localhost:9092",
                         source : String = "dns",
                         suspicious : String = "suspicious",
                         good: String = "good",
                         threshold: Double = .3)

object LDAKStream extends App {

  val appConfig = KStreamConfig()


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
  val textLines: KStream[String, String] = builder.stream[String, String](appConfig.source)

  val branches = textLines.branch(
    /**
      * branch(0) - if max probability is less than .3, then the event is suspicious
      */
    (k, v) =>  {
      val model = ModelConsumer.getModel()

      // Create a new instance named "test instance" with empty target and source fields.
      val event = new InstanceList(model.instances.getPipe)
      event.addThruPipe(new Instance(v, null, "instance", null))

      val inferencer = model.model.getInferencer
      val probabilities = inferencer.getSampledDistribution(event.get(0), 10, 1, 5)

      val stream = util.Arrays.stream(probabilities)
      val p = stream.max.getAsDouble // find the max probability. we don't care which topic it belongs
      p < appConfig.threshold // Threshold, if lower, then doesn't belong to any existing topic
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
    streams.close(java.time.Duration.ofSeconds(10))
  }

}

object ModelConsumer {

  val props = new Properties()
  props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
  props.put(ConsumerConfig.GROUP_ID_CONFIG, "lda.model")
  props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
  props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)
  props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  import scala.concurrent.ExecutionContext.Implicits.global
  var model: Option[LDAModel] = Option.empty

  val future = Future {
    import scala.collection.JavaConverters._
    val modelConsumer = new KafkaConsumer[String, Array[Byte]](props)
    modelConsumer.subscribe(Arrays.asList("lda.model"))
    var tmp: Option[Array[Byte]] = Option.empty
    while(true) {
      val modelRecords = modelConsumer.poll(java.time.Duration.ofSeconds(2))
      if(!modelRecords.isEmpty) {
        tmp = Some(modelRecords.iterator()
          .asScala
          .toStream
          .max(Ordering[Long].on[ConsumerRecord[String, Array[Byte]]](_.timestamp()))
          .value()
        )
      }
      else {
        if(tmp.isEmpty) {
          val partition = new TopicPartition("lda.model", 0)
          modelConsumer.seek(partition, modelConsumer.committed(partition).offset() - 1)
        }
        else {
          model = deserializeModel(tmp.get)
          tmp = Option.empty
        }
      }
    }
  }

  val firstModel = Future {
    while(model.isEmpty) {
      println("waiting for model")
      Thread.sleep(1000)
    }
    model
  }

  private def deserializeModel(bytes: Array[Byte]): Option[LDAModel] = {
    val bais = new ByteArrayInputStream(bytes)
    val in = new ObjectInputStream(bais)
    try {
      Some(in.readObject.asInstanceOf[LDAModel])
    }
    finally {
      if (in != null) in.close()
    }
  }

  def getModel(): LDAModel = {
    if(model.isEmpty) Await.result(firstModel, Duration.Inf).get
    else model.get
  }
}
