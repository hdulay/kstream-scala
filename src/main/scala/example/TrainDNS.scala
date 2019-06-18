package example

import java.io._
import java.nio.file.{Files, Paths}
import java.util._

import example.TrainDNS.model
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

import scala.io.Source



case class TrainingConfig(
 k: Int = 100,
 count: Int = 300000,
 dataDir: String = "./data",
 outDir: String = ".",
 topics: java.util.Collection[String] = Arrays.asList("dns-train", "good"),
 bootstrapServers: String = "localhost:9092",
 artifactory: String = "localhost:8080"
)

class TrainSupport {
  val topLevelDomains = domains()

  def domains(): Array[String] = {
    Source
      .fromInputStream(getClass.getResourceAsStream("/top-level-domains.txt"))
      .getLines()
      .toStream
      .map(_.toLowerCase())
      .toArray
  }

  def prep(line: String): String = {
    line
      .toLowerCase
      .split(" ")
      .map(value => {
        value.replaceAll("(?:\\W)[^\\w]", "")
      })
      .flatMap(value => {
        val toke = value.trim
        val li = toke.lastIndexOf('.')
        val take = toke.length - toke.lastIndexOf('.') - 1
        if(li != -1 && topLevelDomains.contains(toke.takeRight(take))) {
          if(toke.count(_ == '.') > 1) {
            val lefti = toke.take(li).lastIndexOf('.')
            val encoded = toke.take(lefti)
            val enc = if(encoded.length > 10) s"BIGENCODED $encoded" else encoded
            val right = toke.takeRight(toke.length - lefti - 1)
            encoded.split('.') :+ enc :+ right
          } else {
            Array(toke)
          }
        } else {
          Array(toke)
        }
      })
      .mkString(" ")
  }

  def artifactory(bytes: Array[Byte], config: TrainingConfig): String = {
    val name = model.name
    val out = new FileOutputStream(s"${config.outDir}/$name")
    out.write(bytes)
    out.flush()
    out.close()
    s"http://${config.artifactory}/$name"
  }
}

object TrainDNS extends App {

  val logger = LoggerFactory.getLogger("lda-trainer")

  val parser = new scopt.OptionParser[TrainingConfig]("dns.trainer") {
    head("dns.trainer", "0.1")

    opt[Int]('k', "topics")
      .optional()
      .action( (k, c) => c.copy(k = k) )
      .text("number of topics to train on")

    opt[Int]('c', "topics")
      .optional()
      .action( (k, c) => c.copy(count = k) )
      .text("number of records to train on")

    opt[String]('d', "dataDir")
      .optional()
      .action( (x, c) => c.copy(dataDir = x) )
      .text("directory containing the data files to train on")

    opt[String]('b', "bootstrapServers")
      .optional()
      .action( (x, c) => c.copy(bootstrapServers = x) )
      .text("kafka bootstrap host:port")

    opt[String]('a', "artifactory")
      .optional()
      .action( (x, c) => c.copy(artifactory = x) )
      .text("artifactory host:port")

    opt[String]('o', "outDir")
      .optional()
      .action( (x, c) => c.copy(outDir = x) )
      .text("output directory where the model should be written and artifactory will pickup")
  }

  val config = parser.parse(args, TrainingConfig()).get
  println(config)

  val topics = config.topics
  val k = config.k
  val bootstrap_servers = config.bootstrapServers

  val props = new Properties()
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap_servers)
  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)


  /**
    * Produce the model
    */
  val producer = new KafkaProducer[String, String](props)

  sys.addShutdownHook {
    println("exiting")
  }

  val support = new TrainSupport()
  val topLevelDomains = support.domains()

  val docs = Files
    .list(Paths.get(config.dataDir)) // loads all files in this directory including feedback from connector
    .filter(Files.isRegularFile(_))  // filtering out subdirectories
    .flatMap[String](path => Files.lines(path)) // create a records per line in all files
    .toArray
    .reverse                         // reversing to get last 300k
    .slice(0, config.count)          // limiting to 300k
    .map(line => support.prep(line.toString))
    .asInstanceOf[Array[String]]

  val baos = new ByteArrayOutputStream()
  val model = LDAModel.train(docs, k)
  val out = new ObjectOutputStream(baos)
  out.writeObject(model)

  println(s"training size: ${docs.size}")
  val path = support.artifactory(baos.toByteArray, config)
  out.close()

  val record = new ProducerRecord[String, String] ("lda-model","lda.model", path)
  val future = producer.send(record, (metadata: RecordMetadata, exception: Exception) => {
    if (exception != null) println(exception)
  })

  println(s"Model has been serialized to topic: ${future.get().topic()}")
  producer.flush()
  producer.close()


}
