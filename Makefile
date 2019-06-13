build: scala dbuild

dbuild:
	docker-compose build

scala:
	docker run --rm -it -v ${PWD}:/project -w /project hseeberger/scala-sbt sbt clean assembly

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
	mv -f dns.log data
	rm -f *.log

ddata: zeek clean-zeek

cluster: up topics connect

up:
	docker-compose up -d
	sleep 20

topics:
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic dns --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic suspicious --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic good --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create \
		--topic lda-model \
		--partitions 1 \
		--replication-factor 1 \
		--config retention.ms=60000 \
		--config cleanup.policy=compact

connect:
	echo "starting connect file sink"
	sleep 20
	docker exec -it connect curl -d "@/project/docker/file-connector/create-file-connector.json" \
		-X PUT \
		-H "Content-Type: application/json" \
		http://connect:8083/connectors/feedback/config | jq 

producer:
	docker exec -it broker kafka-console-producer --broker-list localhost:9092 --topic dns

lda-model:
	docker exec -it broker kafka-console-consumer --bootstrap-server localhost:9092 --topic lda-model --from-beginning

suspicious:
	docker exec -it broker kafka-console-consumer --bootstrap-server localhost:9092 --topic suspicious

good:
	docker exec -it broker kafka-console-consumer --bootstrap-server localhost:9092 --topic good

dns-train:
	docker exec -it broker kafka-console-consumer --bootstrap-server localhost:9092 --topic dns-train --from-beginning

clist:
	docker exec -it connect curl http://connect:8083/connectors | jq

cstatus:
	docker exec -it connect curl http://connect:8083/connectors/dns.logs/status | jq

crestart:
	docker exec -it connect curl -X POST http://connect:8083/connectors/dns.logs/restart | jq 

ksql:
	# docker exec -it ksql-cli echo "create stream bad2 (data VARCHAR) WITH (KAFKA_TOPIC='suspicious', VALUE_FORMAT='DELIMITED');" > q
	# docker exec -it ksql-cli http://ksql-server:8088 < q
	docker exec -it ksql-cli ksql http://ksql-server:8088

down:
	docker-compose down

ps:
	docker-compose ps