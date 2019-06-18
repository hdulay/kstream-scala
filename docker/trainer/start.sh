#!/bin/bash
sleep 120
for i in {1..100}
do 
	scala \
		-J-Xmx3g \
		-classpath /project/target/scala-2.12/kstream-scala-assembly-0.1.0-SNAPSHOT.jar example.TrainDNS \
		-d /project/data \
		-b broker:29092 \
		-a artifactory:8080 \
		-o /project \
		-c 300000
	sleep 60
done