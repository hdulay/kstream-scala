package example

import java.io._
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.regex.Pattern

import cc.mallet.pipe._
import cc.mallet.pipe.iterator.StringArrayIterator
import cc.mallet.topics.ParallelTopicModel
import cc.mallet.types.{Instance, InstanceList}


@SerialVersionUID(1L)
object LDAModel {
  @throws[Exception]
  def load(path: String): LDAModel = { // Reading the object from a file
    val file = new FileInputStream(path)
    val in = new ObjectInputStream(file)
    try {
      in.readObject.asInstanceOf[LDAModel]
    }
    finally {
      if (file != null) file.close()
      if (in != null) in.close()
    }
  }

  @throws[Exception]
  def train(docs: Array[String], k: Int = 20): LDAModel = { // Begin by importing documents from text to feature sequences
    val pipeList = new java.util.ArrayList[Pipe]
    // Pipes: lowercase, tokenize, remove stopwords, map to features

    val regex = "\\b(\\w*[^\\d][\\w\\.\\:]*\\w)\\b"
    pipeList.add(new CharSequence2TokenSequence(Pattern.compile(regex)))
//    pipeList.add(new TokenSequenceRemoveStopwords(new File("stopwords.txt"), "UTF-8", false, false, false))
    pipeList.add(new TokenSequence2FeatureSequence)
    val sp = new SerialPipes(pipeList)
    val instances = new InstanceList(sp)

    val sai = new StringArrayIterator(docs)
    instances.addThruPipe(sai) // data, label, name fields

    // Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
    //  Note that the first parameter is passed as the sum over topics, while
    //  the second is
    val model = new ParallelTopicModel(k, 1.0, 0.01)
    model.addInstances(instances)
    // Use two parallel samplers, which each look at one half the corpus and combine
    //  statistics after every iteration.
    model.setNumThreads(4)
    // Run the model for 50 iterations and stop (this is for testing only,
    //  for real applications, use 1000 to 2000 iterations)
    model.setNumIterations(50)
    model.estimate()

    val sdf = new SimpleDateFormat("yyyyMMddHHmmss")
    val name = s"lda.model.${sdf.format(new Date(System.currentTimeMillis()))}_size${instances.size()}"
    LDAModel(model, instances, name)
  }
}

class DomainPipe extends Pipe {
  override def pipe(carrier: Instance): Instance = {
    if (carrier.getData.isInstanceOf[CharSequence]) {
      val data = carrier.getData.asInstanceOf[CharSequence]

      carrier.setData(data.toString.toLowerCase)
    }
    else throw new IllegalArgumentException("CharSequenceLowercase expects a CharSequence, found a " + carrier.getData.getClass)
    carrier
  }

  // Serialization

  private val serialVersionUID = 1
  private val CURRENT_SERIAL_VERSION = 0

  @throws[IOException]
  private def writeObject(out: ObjectOutputStream): Unit = {
    out.writeInt(CURRENT_SERIAL_VERSION)
  }

  @throws[IOException]
  @throws[ClassNotFoundException]
  private def readObject(in: ObjectInputStream): Unit = {
    val version = in.readInt
  }
}

@SerialVersionUID(1L)
case class LDAModel(model: ParallelTopicModel, instances: InstanceList,
                    name: String = s"lda.model.${Date.from(Instant.now())}")
  extends Serializable {

  override def toString: String = {
    // The data alphabet maps word IDs to strings
    val dataAlphabet = instances.getDataAlphabet
    // Estimate the topic distribution of the first instance,
    //  given the current Gibbs state.
    val topicDistribution = model.getTopicProbabilities(0)
    val topicSortedWords = model.getSortedWords
    val sb = new StringBuffer
    // Show top 5 words in topics with proportions for the first document
    var topic = 0
    val size = topicSortedWords.size
    while (topic < size) {
      val iterator = topicSortedWords.get(topic).iterator
      sb.append("%d\t%.3f\t".format(topic, topicDistribution(topic)))
      var rank = 0
      while ( {
        iterator.hasNext && rank < 9
      }) {
        val idCountPair = iterator.next
        sb.append("%s (%.0f) ".format(dataAlphabet.lookupObject(idCountPair.getID), idCountPair.getWeight))
        rank += 1
      }
      sb.append("\n")

      {
        topic += 1; topic - 1
      }
    }
    sb.toString
  }
}