build:
	sbt assembly

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

clean-zeek: 
	rm -f *.pcap
	rm -f *.bro
	rm -f *.zip
	mv -f *.log data

ddata: zeek clean-zeek


cluster:
	docker-compose build
	docker-compose up -d

connect:
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic dns --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create 
		--topic good \
		--partitions 1 \
		--replication-factor 1 \
		--partitions 1 \
		--replication-factor 1 \
		--config retention.ms=60000
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create \
		--topic lda.model \
		--partitions 1 \
		--replication-factor 1 \
		--config max.message.bytes=1000000000 \
		--config retention.ms=60000 \
		--config cleanup.policy=compact \
		--config compression.type=gzip
	docker exec -it connect curl -d "@/project/docker/file-connector/create-file-connector.json" \
		-X PUT \
		-H "Content-Type: application/json" \
		http://connect:8083/connectors/dns.logs/config | jq 


train:
	scala -J-Xmx2g -classpath target/scala-2.12/kstream-scala-assembly-0.1.0-SNAPSHOT.jar example.TrainDNS localhost:9092

kstream:
	scala -J-Xmx2g -classpath target/scala-2.12/kstream-scala-assembly-0.1.0-SNAPSHOT.jar example.LDAKStream

producer:
	docker exec -it broker kafka-console-producer --broker-list localhost:9092 --topic dns

suspicious:
	docker exec -it broker kafka-console-consumer --broker-list localhost:9092 --topic suspicious

good:
	docker exec -it broker kafka-console-consumer --broker-list localhost:9092 --topic good

connect-list:
	docker exec -it connect curl http://connect:8083/connectors | jq

connect-status:
	docker exec -it connect curl http://connect:8083/connectors/dns.logs/status | jq

connect-restart:
	docker exec -it connect curl -X POST http://connect:8083/connectors/dns.logs/restart | jq 

ksql:
	# docker exec -it ksql-cli echo "create stream bad2 (data VARCHAR) WITH (KAFKA_TOPIC='suspicious', VALUE_FORMAT='DELIMITED');" > q
	# docker exec -it ksql-cli http://ksql-server:8088 < q
	docker exec -it ksql-cli ksql http://ksql-server:8088