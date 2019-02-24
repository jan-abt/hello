import java.io.Closeable

import scala.sys.process.Process
import scala.util.Random

import sys.process._

organization in ThisBuild := "com.example"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.12.4"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.0" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test
//port number between [49152, 65535]
val port = 49152 + Random.nextInt((65535-49152)+1)


// do not delete database files on start
lagomCassandraCleanOnStart in ThisBuild := false

lazy val `hello` = (project in file("."))
  .aggregate(`hello-api`, `hello-impl`, `hello-stream-api`, `hello-stream-impl`)

lazy val `hello-api` = (project in file("hello-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `hello-impl` = (project in file("hello-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`hello-api`)

lazy val `hello-stream-api` = (project in file("hello-stream-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `hello-stream-impl` = (project in file("hello-stream-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .dependsOn(`hello-stream-api`, `hello-api`)

//custom service, define a task to start the service
val startElasticSearch = taskKey[Closeable]("Starts elastic search")

  startElasticSearch in ThisBuild := {

    val esVersion = "6.6.1"
    val log = streams.value.log
    val `elastic-search` = target.value / s"elasticsearch-$esVersion"

    if (!`elastic-search`.exists()) {

      log.info(s"Downloading Elastic Search v.$esVersion...")
      IO.unzipURL(url(s"https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-$esVersion.zip"),  target.value)
      //give read write execute access
      s"""chmod -R 777 ${`elastic-search`}""" !
      
      IO.append(`elastic-search` / "config" / "log4j2.properties", "\nrootLogger.level = warn\n")
    } else {
      log.info(s"Starting Elastic Search...")
    }
    val binFile = if (sys.props("os.name") == "Windows") {
      `elastic-search` / "bin" / "elasticsearch.bat"
    } else {
      `elastic-search` / "bin" / "elasticsearch"
    }

    val process = Process(binFile.getAbsolutePath, `elastic-search`).run(log)

    log.info(s"Service Elastic Search is running at  http://localhost:${port}")

    new Closeable {//called when shut down is in progess
      override def close(): Unit = {
        log.info(s"Stopping Elastic Search...")
        process.destroy()
        log.info(s"Elastic Search is stopped.")

      }
    }
  }

  //to avoid port collisions, assign custom port
  lagomUnmanagedServices in ThisBuild := Map("elastic-search"  -> s"http://localhost:${port}")
  //so that at will be picked up by addAll
  lagomInfrastructureServices in ThisBuild +=  (startElasticSearch in ThisBuild).taskValue

lazy val setPermissionsForElasticSearch = taskKey[Unit]("Execute the shell script")

 setPermissionsForElasticSearch := {

  // import scala.sys.process._
   //change permissions
   //"find ~/Lagom/hello/target/elasticsearch-5.4.0/bin -type f -exec chmod 777 {};" !
   import scala.sys.process.Process
   Process(s"find ~/Lagom/hello/target/elasticsearch-5.4.0/bin -type f -exec chmod 777 {} \\;") !

 }