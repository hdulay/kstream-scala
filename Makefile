build:
	sbt assembly

cluster:
	docker-compose up -d
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic dns --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic lda.model --partitions 1 --replication-factor 1 --config max.message.bytes=1000000

download: 
	wget https://github.com/blacktop/docker-zeek/raw/master/pcap/heartbleed.pcap  -O data.pcap
	wget https://github.com/blacktop/docker-zeek/raw/master/scripts/local.bro -O local.bro
	wget https://s3-us-west-2.amazonaws.com/apachespot/public_data_sets/dns_aws/dns_pcap_synthetic_sample.zip
	unzip dns_pcap_synthetic_sample.zip

zeek: download
	docker run --rm \
         -v `pwd`:/pcap \
         -v `pwd`/local.bro:/usr/local/bro/share/bro/site/local.bro \
         blacktop/zeek -r dns_test01_00100_20160707221101.pcap local "Site::local_nets += { 192.168.11.0/24 }"

clean: 
	rm -f *.pcap
	rm -f *.bro
	rm -f *.zip
	mv -f *.log data

ddata: zeek clean

train:
	scala -J-Xmx2g -classpath target/scala-2.12/kstream-scala-assembly-0.1.0-SNAPSHOT.jar example.TrainDNS

kstream:
	scala -J-Xmx2g -classpath target/scala-2.12/kstream-scala-assembly-0.1.0-SNAPSHOT.jar example.LDAKStream

producer:
	docker exec -it broker kafka-console-producer --broker-list localhost:9092 --topic dns

suspicious:
	docker exec -it broker kafka-console-consumer --broker-list localhost:9092 --topic suspicious

good:
	docker exec -it broker kafka-console-consumer --broker-list localhost:9092 --topic good

