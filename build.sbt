import Dependencies._
import sbt.Keys.libraryDependencies

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

resolvers += "confluent" at "http://packages.confluent.io/maven/"

lazy val root = (project in file("."))
	.settings(
		name := "kstream-scala",
		libraryDependencies += scalaTest % Test,
		// https://mvnrepository.com/artifact/cc.mallet/mallet  // This is the LDA library
		libraryDependencies += "cc.mallet" % "mallet" % "2.0.8",

		// confluent
		libraryDependencies += "io.confluent.ksql" % "ksql-udf" % "5.2.1",

		// scala
		libraryDependencies += "org.apache.kafka" % "kafka-streams-scala_2.12" % "2.2.0",

		// https://mvnrepository.com/artifact/org.apache.kafka/kafka-clients
		libraryDependencies += "org.apache.kafka" % "kafka-clients" % "2.2.0",

			// https://mvnrepository.com/artifact/com.github.scopt/scopt
		libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.1",

		libraryDependencies += "net.sf.py4j" % "py4j" % "0.10.8.1"

	)

