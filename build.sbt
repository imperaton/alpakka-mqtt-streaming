name := "alpakka-mqtt-streaming"

version := "1.0"

scalaVersion := "2.12.11"

lazy val akkaVersion = "2.6.4"
lazy val akkaHttpVersion = "10.2.7"

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-mqtt-streaming" % "2.0.1",
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
