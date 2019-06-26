build: sbuild dbuild

dbuild:
	docker-compose build

sbuild:
	docker run --rm -it -v ${PWD}:/project -w /project hseeberger/scala-sbt sbt clean assembly

tcpdump: 
	sudo tcpdump port 53 >> data/dns.log

cluster: up topics connect

up:
	docker-compose up -d
	echo "waiting for cluster"
	sleep 20

topics:
	echo "creating topics"
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic dns --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic suspicious --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic good --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic dns-scores --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create \
		--topic lda-model \
		--partitions 1 \
		--replication-factor 1 \
		--config retention.ms=60000 \
		--config cleanup.policy=compact

connect:
	echo "starting connect file sink"
	sleep 30
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

scores:
	docker exec -it broker kafka-console-consumer \
		--bootstrap-server broker:9092  \
        --topic dns-scores \
        --from-beginning \
        --formatter kafka.tools.DefaultMessageFormatter \
        --property print.key=true \
        --property key.deserializer=org.apache.kafka.common.serialization.StringDeserializer \
        --property value.deserializer=org.apache.kafka.common.serialization.StringDeserializer

clist:
	docker exec -it connect curl http://connect:8083/connectors | jq

cstatus:
	docker exec -it connect curl http://connect:8083/connectors/feedback/status | jq

crestart:
	docker exec -it connect curl -X POST http://connect:8083/connectors/feedback/restart | jq 

ksql:
	docker exec -it ksql-cli ksql http://ksql-server:8088

down:
	docker-compose down

ps:
	docker-compose ps

clean:
	rm lda.*
