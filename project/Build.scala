import com.typesafe.sbt.SbtScalariform.scalariformSettings
import sbt._
import sbtassembly.Plugin._
import AssemblyKeys._
import Keys._

import ImplicitUtil.SeqSettingsProject

object AlpineRConnectorBuild extends Build {

  lazy val sharedSettings = Defaults.defaultSettings ++ Seq(  
    version := "0.1",
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
                      "Spray Repo" at "http://repo.spray.io"),
    libraryDependencies ++= Seq(
    	"org.scalatest" %% "scalatest" % "1.9.1" % "test"
    ),
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
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
      case PathList("META-INF", xs @ _*) =>
	    (xs map {_.toLowerCase}) match {
	      case ("MANIFEST.MF" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) => MergeStrategy.discard
	      case _ => MergeStrategy.discard
	  }
      case _ => MergeStrategy.first 
      }
    }
  ) ++ scalariformSettings ++ assemblySettings

  lazy val akkaSettings = Seq(
  	libraryDependencies ++= {
      val akkaVersion = "2.3.2"
  	  Seq(
  	    "com.typesafe.akka"    %%    "akka-actor"                    %    akkaVersion,
        "com.typesafe.akka"    %%    "akka-remote"                   %    akkaVersion,
        "com.typesafe.akka"    %%    "akka-slf4j"                    %    akkaVersion,
        "com.typesafe.akka"    %%    "akka-testkit"                  %    akkaVersion,
        "com.typesafe.akka"    %%    "akka-kernel"                   %    akkaVersion,
        "com.typesafe.akka"    %%    "akka-cluster"                  %    akkaVersion,
        "com.typesafe.akka"    %%    "akka-persistence-experimental" %    akkaVersion
  	  )
    }
  )

  lazy val root = project
    .in(file("."))
    .aggregate(messages, server, sample_client)
    .settings(sharedSettings)
    .settings(name := "alpine-r-connector")
  
  lazy val messages = project.settings(sharedSettings)

  lazy val server = project.settings(sharedSettings ++ akkaSettings).dependsOn(messages)

  /* not camelCase, but named the same as directory since Scala's macro will pick it up;
     otherwise, you would have to write the Project() boilerplate
   */
  lazy val sample_client = project
    .settings(sharedSettings ++ akkaSettings)
    .dependsOn(messages, server)
    .settings(
      libraryDependencies ++= Seq(
      	"org.mockito" % "mockito-all" % "1.9.5"
      )
    )
}