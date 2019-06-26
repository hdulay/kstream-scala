import Dependencies._
import sbt.Keys.libraryDependencies


ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

resolvers += "confluent" at "http://packages.confluent.io/maven/"
resolvers += Classpaths.typesafeReleases
resolvers += Resolver.jcenterRepo
resolvers += "central" at "http://central.maven.org/maven2/"

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

		// https://mvnrepository.com/artifact/commons-io/commons-io
		libraryDependencies += "commons-io" % "commons-io" % "2.6",

			// https://mvnrepository.com/artifact/com.google.code.gson/gson
		libraryDependencies += "com.google.code.gson" % "gson" % "2.8.5",

		// https://mvnrepository.com/artifact/com.github.scopt/scopt
		libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.1",

		// https://mvnrepository.com/artifact/org.apache.kafka/connect-api
		libraryDependencies += "org.apache.kafka" % "connect-api" % "2.2.0",

		libraryDependencies += "net.sf.py4j" % "py4j" % "0.10.8.1"

	)

assemblyExcludedJars in assembly := {
    val cp = (fullClasspath in assembly).value
    cp filter { f =>
      f.data.getName.contains("log4j")
    }
  }

assemblyMergeStrategy in assembly := {
	case PathList("org", "apache", xs @ _*)         => MergeStrategy.first
	case PathList("org", "slf4j", xs @ _*)         => MergeStrategy.first
	case x =>
		val oldStrategy = (assemblyMergeStrategy in assembly).value
		oldStrategy(x)
}

