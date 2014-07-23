import sbt._
import Keys._
import sbtassembly.Plugin._

object BuildSettings {
  val buildOrganization = "uk.co.devsoup"
  val buildVersion      = "1.0.3-SNAPSHOT"
  val buildScalaVersion = "2.11.1"

  val buildSettings = Seq (
    organization := buildOrganization,
    version      := buildVersion,
    scalaVersion := buildScalaVersion,
    crossPaths := false
  )
}

object Dependencies {
  val httpclient = "org.apache.httpcomponents" % "httpclient" % "4.3.3"
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.1"
  val commonslang3 = "org.apache.commons" % "commons-lang3" % "3.2.1"
}

object MingleStoryPullerBuild extends Build {
  import Dependencies._
  import BuildSettings._


  val serviceDependancies = Seq (
    httpclient,
    logback,
    commonslang3
  )

  lazy val mingleDependencies = Project (
    "MingleDependencies",
    file ("."),
    settings = buildSettings ++ assemblySettings ++ Seq (libraryDependencies ++= serviceDependancies)
  )

}
