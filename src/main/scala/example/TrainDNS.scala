package example

import scopt.OptionParser

case class TrainingConfig(
                           data: String = "/Users/hubert.dulay/development/cyber-data/dns-data",
                           model: String = "./dns.lda.model",
                           k: Int = 100
                         )

object TrainDNS extends App {

  val parser = new OptionParser[TrainingConfig]("scopt") {
    head("scopt", "3.x")

    opt[String]('d', "data").action((x, c) =>
      c.copy(data = x)).text("directory path where the training data exists")

    opt[String]('m', "model").action((x, c) =>
      c.copy(model = x)).text("the file name of the serialized model")

    opt[Int]('k', "clusters").action((x, c) =>
      c.copy(k = x)).text("the number of clusters to create")
  }

  val config = parser.parse(args, TrainingConfig()).get

  val LDA_MODEL = config.model

  new LDAModel().train(config.data,config.k).save(LDA_MODEL)

  println("loading model")
  val lda = LDAModel.load(LDA_MODEL)
  println("model loaded")

  System.out.println(lda)
}
