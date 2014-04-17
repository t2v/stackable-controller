import sbt._
import Keys._
import play.Project._

object StackableControllerProjects extends Build {

  lazy val _organization = "jp.t2v"

  lazy val _version = "0.4.0-SNAPSHOT"

  def _publishTo(v: String) = {
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")  
    else Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }

  lazy val _resolvers = Seq(
    "typesafe repo" at "http://repo.typesafe.com/typesafe/repo",
    "typesafe releases" at "http://repo.typesafe.com/typesafe/releases",
    "sonatype releases" at "http://oss.sonatype.org/content/repositories/releases",
    "sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots"
  )

  lazy val _scalacOptions = Seq("-unchecked")

  lazy val _pomExtra = {
    <url>https://github.com/t2v/stackable-controller</url>
    <licenses>
      <license>
        <name>Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:stackable-controller</url>
      <connection>scm:git:git@github.com:stackable-controller</connection>
    </scm>
    <developers>
      <developer>
        <id>gakuzzzz</id>
        <name>gakuzzzz</name>
        <url>https://github.com/gakuzzzz</url>
      </developer>
    </developers>
  }

  lazy val core = Project(
    id = "core", 
    base = file("core"), 
    settings = Defaults.defaultSettings ++ Seq(
      organization := _organization,
      name := "stackable-controller",
      version := _version,
      scalaVersion := "2.10.4",
      publishTo <<= version { (v: String) => _publishTo(v) },
      publishMavenStyle := true,
      resolvers ++= _resolvers,
      libraryDependencies ++= Seq(
          // scope: compile
          "com.typesafe.play" %% "play" % "2.3-M1" % "provided"
      ),
      sbtPlugin := false,
      scalacOptions ++= _scalacOptions,
      publishMavenStyle := true,
      publishArtifact in Test := false,
      pomIncludeRepository := { x => false },
      pomExtra := _pomExtra
    )
  )

  lazy val sampleDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    "com.typesafe.play"  %% "play"                      % "2.3-M1",
    "com.github.seratch" %% "scalikejdbc"               % "[1.6,)",
    "com.github.seratch" %% "scalikejdbc-interpolation" % "[1.6,)",
    "com.github.seratch" %% "scalikejdbc-play-plugin"   % "[1.6,)",
    "org.slf4j"          %  "slf4j-simple"              % "[1.7,)"
  )

  lazy val sample =  play.Project("sample", _version, sampleDependencies, path = file("sample")).settings(
    scalaVersion := "2.10.4"
    // Add your own project settings here
  ) dependsOn(core)

  lazy val root = Project(id = "root", base = file(".")).settings(
    scalaVersion := "2.10.4"
  ).aggregate(core, sample) 

}
