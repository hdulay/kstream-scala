# Introduction
This is an example of how to deploy a machine learning model to a KStreams application. The ML library used here is Mallet:

```
libraryDependencies += "cc.mallet" % "mallet" % "2.0.8",
```

[Mallet](http://mallet.cs.umass.edu/) is a Java based ML library that contains an implementation of a
Latent Dirichlet allocation (LDA) algorithm.
LDA is a topic modeling unsupervised learning algorithm. It takes a corpus of text and classifies them under topics.


## Get Data !!!!!!
In order to run this demo, you'll need some data. The make command below starts a tcpdump process listening to port 53 and capturing dns logs. It then writes the logs into data/dns.log. You will need a significat amount of data for training so let this run all day ( or a couple of days ).
```bash
$ make tcpdump
```

## Running the Demo
Build the scala KStream code and build docker compose
```
$ make build
```

Build and start the Kafka cluster
```
$ make cluster
```

Open 4 terminals
1. Produer
	```
	$ make producer
	```
1. Consumer of suspicious events
	```
	$ make suspicious
	```
1. Consumer of good & bad counts
	```
	$ make agg
	```

1. For KSQL. The KSQL will watch for the suspicious topic and print out the bad messages.
	```
	$ make ksql
	> create stream bad (data VARCHAR) WITH (KAFKA_TOPIC='suspicious', VALUE_FORMAT='DELIMITED');
	> select * from bad;
	```
 
Model training is ongoing. When completed, the model is placed in a topic. The KStreams app waits for a model to appear and uses it to score incoming message.

## Train the model
I train the model using LDA. See TrainDNS.scala. This scala class trains the LDA model and serializes it to a Kafka topic. The model is loaded by the KStreams application to score incoming data from a Kakfa topic then routes suspicious dns requests to a separate topic.

