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

## Train the model
After downloading, extracting and parsing the dns log data, I train the model using LDA. See TrainDNS.scala. This
scala class trains the LDA model and serializes the it to a file. This file is loaded by the KStreams application
to score incoming data from a Kakfa topic then routes suspicious dns requests to a separate topic.


## Start the producer
Start the Kafka cluster. Deploy or run the LDAKStreams.scala application. Depending on how much data you trained the model
with, you might wait for 5 min. The more data you trained on, the bigger the serialized model and the longer the JVM will
take to load it to memory. The application will display `model loaded successfully` when finished loading.

Start the producer with the command below and send it messages copied from your dns logs. It should end up in the good
topic. Next type some random words. This message should end up in the suspicious topic.
```
kafka-console-producer --broker-list localhost:9092 --topic dns
```

