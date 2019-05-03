package example

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util.regex.Pattern
import java.util.stream.Collectors

import cc.mallet.pipe._
import cc.mallet.pipe.iterator.StringArrayIterator
import cc.mallet.topics.ParallelTopicModel
import cc.mallet.types.InstanceList
import scala.collection.JavaConverters._


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
}

@SerialVersionUID(1L)
class LDAModel extends Serializable {
  private var model: ParallelTopicModel = null
  private var instances: InstanceList = null

  def getPipe: Pipe = instances.getPipe

  def getModel: ParallelTopicModel = model

  @throws[Exception]
  def train(dataDir: String, k: Int = 20): LDAModel = { // Begin by importing documents from text to feature sequences
    val pipeList = new java.util.ArrayList[Pipe]
    // Pipes: lowercase, tokenize, remove stopwords, map to features

    val regex = "\\b(\\w*[^\\d][\\w\\.\\:]*\\w)\\b"
    pipeList.add(new CharSequenceLowercase)
    pipeList.add(new CharSequence2TokenSequence(Pattern.compile(regex)))
//    pipeList.add(new TokenSequenceRemoveStopwords(new File("stopwords.txt"), "UTF-8", false, false, false))
    pipeList.add(new TokenSequence2FeatureSequence)
    val sp = new SerialPipes(pipeList)
    this.instances = new InstanceList(sp)
    // Reader fileReader = new InputStreamReader(new FileInputStream(new File(args[0])), "UTF-8");
    val docs = Files
      .list(Paths.get(dataDir))
      .filter(Files.isRegularFile(_))
      .flatMap[String](path => Files.lines(path))
      .collect(Collectors.toList[String])
      .asScala
      .toArray

    val sai = new StringArrayIterator(docs)
    instances.addThruPipe(sai) // data, label, name fields

    // Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
    //  Note that the first parameter is passed as the sum over topics, while
    //  the second is
    this.model = new ParallelTopicModel(k, 1.0, 0.01)
    model.addInstances(instances)
    // Use two parallel samplers, which each look at one half the corpus and combine
    //  statistics after every iteration.
    model.setNumThreads(2)
    // Run the model for 50 iterations and stop (this is for testing only,
    //  for real applications, use 1000 to 2000 iterations)
    model.setNumIterations(50)
    model.estimate()
    this
  }

  @throws[Exception]
  def save(fn: String): LDAModel = { //Saving of object in a file
    val file = new FileOutputStream(fn)
    val out = new ObjectOutputStream(file)
    // Method for serialization of object
    out.writeObject(this)
    out.close()
    file.close()
    System.out.println("Object has been serialized")
    this
  }

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
    while ( {
      topic < size
    }) {
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