FROM hseeberger/scala-sbt

CMD scala -J-Xmx2g \
	-classpath /project/scala-2.12/kstream-scala-assembly-0.1.0-SNAPSHOT.jar \
	example.LDAKStream \
	$KAFKA_BOOTSTRAP_SERVERS

