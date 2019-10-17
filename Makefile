build: sbuild dbuild

dbuild:
	docker-compose build

sbuild:
	#https://blog.scalents.com/2019/01/16/about-packaging-type-error-when-sbt-resolves-dependencies/
	docker run --rm -it -v ${PWD}:/project -w /project mozilla/sbt:1.3.3 sbt clean compile > /dev/null 2>&1 || sbt assembly

cluster: up topics connect

up:
	docker-compose up -d
	echo "waiting for cluster"
	sleep 20

topics:
	echo "creating topics"
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic claims --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic suspicious --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic good --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create --topic claims-scores --partitions 1 --replication-factor 1
	docker exec -it broker kafka-topics --bootstrap-server broker:9092 --create \
		--topic lda-model \
		--partitions 1 \
		--replication-factor 1 \
		--config retention.ms=60000 \
		--config cleanup.policy=compact
	sleep 10

connect:
	docker exec -it connect curl -d "@/project/docker/claims-gen/claims.json" \
		-X PUT \
		-H "Content-Type: application/json" \
		http://connect:8083/connectors/claims-gen/config

	docker exec -it ksql-server curl -X "POST" "http://ksql-server:8088/ksql" \
     -H "Content-Type: application/vnd.ksql.v1+json; charset=utf-8" \
     -d @/project/docker/trainer/ksql.json
	
	docker exec -it connect curl -d "@/project/docker/trainer/trainer.json" \
		-X PUT \
		-H "Content-Type: application/json" \
		http://connect:8083/connectors/trainer/config
		

producer:
	docker exec -it broker kafka-avro-console-producer \
         --broker-list localhost:9092 --topic claims \
         --property value.schema='$(cat /project/claims/claims.avro )'

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

down: clean
	docker-compose down

ps:
	docker-compose ps

clean:
	rm lda.*
