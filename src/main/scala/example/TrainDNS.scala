package example

import java.io.{ByteArrayOutputStream, ObjectOutputStream}
import java.util
import java.util._

import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringDeserializer, StringSerializer}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._

case class TrainingConfig(k: Int = 100)

object TrainDNS extends App {

  val logger = LoggerFactory.getLogger("lda-trainer")

  val props = new Properties()
  props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
  props.put(ConsumerConfig.GROUP_ID_CONFIG, "lda-training")
  props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
  props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
  props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getName)

  /**
    * Setup producer to handle large messages
    */
  props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip")
  props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "1000000000")
  props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "1000000000")

  /**
    * Consume the training data
    */
  val consumer = new KafkaConsumer[String, String](props)
  consumer.subscribe(Arrays.asList("dns-train"))

  /**
    * Produce the model
    */
  val producer = new KafkaProducer[String, Array[Byte]](props)

  sys.addShutdownHook {
    println("exiting")
    consumer.wakeup()
  }

  /**
    * Accumulate all records for training
    */
  var allRecords = new ListBuffer[String]

  breakable {
    while (true) {
      val baos = new ByteArrayOutputStream()
      try {
        consumer.seekToBeginning(util.Collections.emptyList())
        val records = consumer.poll(java.time.Duration.ofMinutes(5))
          .iterator()
          .asScala
          .toArray
          .map(r => r.value())

        /**
          * When there are no more records or if the amount of data is too much,
          * start training
          */
        if(records.isEmpty || allRecords.size > 300000) {
          println(s"training size: ${allRecords.size}")
          val model = LDAModel.train(allRecords.takeRight(300000).toArray, 100)
          val out = new ObjectOutputStream(baos)
          out.writeObject(model)

          val ba = baos.toByteArray
          println(s"model size: ${ba.size}")
          val record = new ProducerRecord[String, Array[Byte]] ("lda.model",
            s"lda-${System.currentTimeMillis()}", ba)
          producer.send(record, (metadata: RecordMetadata, exception: Exception) => {
            if (exception == null) {
              println(s"Model has been serialized to topic : ${metadata.topic()}")
            }
            else println(exception)
          })
          break
        }

        /**
          * Accumulate the records
          */
        allRecords ++= records

      }
      catch {
        case w: WakeupException => {
          println(s"received shutdown signal: $w")
          consumer.close()
          producer.close()
          break
        }
        case e: Throwable => {
          println(e)
          break
        }
      }
      finally {
        baos.close()
      }
    }
  }

}
