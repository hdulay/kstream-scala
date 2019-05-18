# Introduction
This is an example of how to deploy a machine learning model to a KStreams application. The ML library used here is Mallet:

```
libraryDependencies += "cc.mallet" % "mallet" % "2.0.8",
```

[Mallet](http://mallet.cs.umass.edu/) is a Java based ML library that contains an implementation of a
Latent Dirichlet allocation (LDA) algorithm.
LDA is a topic modeling unsupervised learning algorithm. It takes a corpus of text and classifies them under topics.

The data used here is DNS data which can be found here:
```
wget https://s3-us-west-2.amazonaws.com/apachespot/public_data_sets/dns_aws/dns_pcap_synthetic_sample.zip
```

In the zip file are pcap files. To decompress and parse the pcap files, I used a docker image containing zeek (bro).
```
docker run --rm -v `pwd`:/pcap -v `pwd`/local.bro:/usr/local/bro/share/bro/site/local.bro blacktop/zeek -r heartbleed.pcap local "Site::local_nets += { 192.168.11.0/24 }"
```

You don't have to use the data above nor does it have to be DNS log events. You can train your own data.


## Make Steps
Build the scala code. You will need to install both sbt and scala 2.12 using Homebrew.
```
$ make build
```

Download the data
```
$ make ddata
```

Build and start the Kafka cluster
```
$ make cluster
```

Build the connect worker and topics
```
$ make connect
```

Open 4 terminals
1. For training the model. This app will train every 30 seconds.
	```
	$ make train
	```
1. For KStreams realtime scoring. This will load new models the training app is producing. The models will have a timestamp on them to show when they were created.
	```
	$ make kstream
	```
1. For KSQL. The KSQL will watch for the suspicious topic and print out the bad messages.
	```
	$ make ksql
	> create stream bad (data VARCHAR) WITH (KAFKA_TOPIC='suspicious', VALUE_FORMAT='DELIMITED');
	> select * from bad;
	```
1. For a Kafka producer. You can copy some good messages in [the training data](data/dns.log) and paste them in the producer for good messages. Type anything else for bad messages.
	```
	$ make producer
	```

Model training is ongoing. When completed, the model is placed in a topic. The KStreams app waits for a model to appear and uses it to score incoming message.


## Train the model
I train the model using LDA. See TrainDNS.scala. This scala class trains the LDA model and serializes it to a Kafka topic. The model is loaded by the KStreams application to score incoming data from a Kakfa topic then routes suspicious dns requests to a separate topic.

