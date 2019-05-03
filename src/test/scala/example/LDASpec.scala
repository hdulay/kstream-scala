package example

import java.util

import cc.mallet.topics.TopicInferencer
import cc.mallet.types.{Instance, InstanceList}
import org.scalatest.FlatSpec

class LDASpec extends FlatSpec {

  val LDA_MODEL = "./dns.lda.model"

  "A real DSN log" should "have probability of greater than .3" in {
    // Create a new instance named "test instance" with empty target and source fields.
    val lda = LDAModel.load(LDA_MODEL)
    val testing: InstanceList = new InstanceList(lda.getPipe)
    val data = "1467947443.579979       C6VE6HjU0DRyxTJq3       172.16.0.113    8473    10.0.2.64       53      udp     48260   0.018892        www.google.com  1       C_INTERNET      1       A       0       NOERROR F       F       T       T       0       10.0.2.146      60.000000       F"
    testing.addThruPipe(new Instance(data, null, "test", null))

    val inferencer: TopicInferencer = lda.getModel.getInferencer
    val testProbabilities: Array[Double] = inferencer.getSampledDistribution(testing.get(0), 10, 1, 5)
    println(util.Arrays.toString(testProbabilities))

    val stream = util.Arrays.stream(testProbabilities)
    val p = stream.max.getAsDouble
    val topic = testProbabilities.indexOf(p)

    println(s"$topic $p")

    assert(p > .3)

  }

}
