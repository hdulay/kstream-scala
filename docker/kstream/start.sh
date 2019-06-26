#!/bin/bash
sleep 120
scala \
	-J-Xmx2g \
	-classpath /project/target/scala-2.12/kstream-scala-assembly-0.1.0-SNAPSHOT.jar example.LDAKStream \
	-b broker:29092 \
	-r schema-registry:8081
