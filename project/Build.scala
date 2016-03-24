import com.typesafe.sbt.SbtScalariform.scalariformSettings
import sbt._
import sbtassembly.Plugin._
import AssemblyKeys._
import Keys._

import ImplicitUtil.SeqSettingsProject

object AlpineRConnectorBuild extends Build {

  val filterAppConf = (ms: Seq[(File,String)]) =>
      ms filter { case (file, toPath) => !toPath.endsWith("application.conf") }

  lazy val sharedSettings = Defaults.defaultSettings ++ scalariformSettings ++ assemblySettings  ++ Seq(  
    version := "0.7",
    organization := "com.alpine",
    scalaVersion := "2.10.3",
    fork := true,
    fork in Test := false,
    javaOptions ++= Seq("-Xmx2G", "-Xms64M", "-XX:MaxPermSize=512M", "-XX:+UseConcMarkSweepGC", "-XX:+CMSClassUnloadingEnabled"),
    parallelExecution := true,
    parallelExecution in Test := false,
    pollInterval := 1000,
    resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
                      "Maven Central" at "http://repo1.maven.org",
                      "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
                      "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
                      "Spray Repo" at "http://repo.spray.io",
                      "spray nightlies repo" at "http://nightlies.spray.io"),
    libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.1.6" % "test",
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions ++= Seq("-unchecked", "-deprecation"),
    shellPrompt <<= name(name => { state: State =>
	  object devnull extends ProcessLogger {
		  def info(s: => String) {}
		  def error(s: => String) {}
		  def buffer[T](f: => T): T = f
	  }
	  val current = """\*\s+(\w+)""".r
	  def gitBranches = ("git branch --no-color" lines_! devnull mkString)
	    "%s:%s>" format (
	      name,
		  current findFirstMatchIn gitBranches map (_.group(1)) getOrElse "-"
  	    )
    }),
    mappings in (Compile, packageBin) ~= { filterAppConf },
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
      case "application.conf"     => MergeStrategy.discard
      case x => old(x)
    }},
    test in assembly := {}
  )

  lazy val akkaSettings = Seq(
  	libraryDependencies ++= {
      //val akkaVer = "2.3.4-spark"
      val akkaVer = "2.3.11"
      val apacheHttpVer = "4.3.3"
  	  Seq(
      /*
        "org.spark-project.akka"     %%    "akka-actor"                    %    s"$akkaVer",
        "org.spark-project.akka"     %%    "akka-remote"                   %    s"$akkaVer",
        "org.spark-project.akka"     %%    "akka-slf4j"                    %    s"$akkaVer",
        "org.spark-project.akka"     %%    "akka-testkit"                  %    s"$akkaVer" % "test",
      */
        "com.typesafe.akka"    %% "akka-actor"       % s"$akkaVer",
        "com.typesafe.akka"    %% "akka-remote"      % s"$akkaVer",
        "com.typesafe.akka"    %% "akka-slf4j"       % s"$akkaVer",
        "com.typesafe.akka"    %% "akka-testkit"     % s"$akkaVer" % "test",
        "org.apache.commons"         %     "commons-lang3"                 %    "3.3.2",
        "org.apache.httpcomponents"  %     "httpclient"                    %    s"$apacheHttpVer",   
        "org.apache.httpcomponents"  %     "httpcore"                      %    s"$apacheHttpVer",   
        "org.apache.httpcomponents"  %     "httpmime"                      %    s"$apacheHttpVer",     
        "com.jsuereth"               %%    "scala-arm"                     %    "1.4"
        
       // "io.spray"                  %     "spray-client"                  %    s"1.2.2-20141105"
      //  "com.typesafe.akka"    %%    "akka-kernel"                   %    akkaVersion,
      //  "com.typesafe.akka"    %%    "akka-cluster"                  %    akkaVersion
      //  "com.typesafe.akka"    %%    "akka-persistence-experimental" %    akkaVersion
  	  )
    }
  )

  // 1.2.2
  // libraryDependencies += "io.spray" % "spray-can" % "1.0"

  lazy val root = project
    .in(file("."))
    .aggregate(messages, server)
    .settings(sharedSettings)
    .settings(
      artifact in (Compile, assembly) ~= { art =>
        art.copy(`classifier` = Some("assembly"))
     }).settings(
      addArtifact(artifact in (Compile, assembly), assembly).settings: _*)
  
  lazy val messages = project.settings(sharedSettings ++ akkaSettings)

  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.0.9"

  lazy val server = project
    .dependsOn(messages)
    .settings(sharedSettings ++ akkaSettings)
    .settings(artifact in (Compile, assembly) ~= { art =>
        art.copy(`classifier` = Some("assembly"))
     })
    .settings(libraryDependencies ++= Seq(
      "co.paralleluniverse" % "quasar-core" % "0.6.1",
      "ch.qos.logback" % "logback-classic" % "1.0.9",
      "org.mockito" % "mockito-all" % "1.9.5" % "test",
      "commons-io"                 %     "commons-io"                    %    "2.4"
     ))
    .settings(resourceDirectory in Compile := baseDirectory.value / "src" / "main" / "resources")
    .settings(jarName in assembly := "alpine-r-connector.jar")
  
}