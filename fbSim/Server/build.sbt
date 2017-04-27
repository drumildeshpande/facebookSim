name := "FacebookServer"

version := "1.0"

scalaVersion:= "2.11.7"

libraryDependencies ++= {
val akkaV = "2.3.13"
val sprayV = "1.3.2"
Seq(
"io.spray" %% "spray-json" % "1.3.1",
"org.json4s" %% "json4s-native" % "3.2.9",
"io.spray" %% "spray-can" % sprayV,
"io.spray" %% "spray-routing" % sprayV,
"io.spray" %% "spray-client" % sprayV,
"com.typesafe.akka" %% "akka-actor" % akkaV,
"com.typesafe.akka" %% "akka-remote" % akkaV,
"org.specs2" %% "specs2-core" % "2.3.11" % "test"
)
}
