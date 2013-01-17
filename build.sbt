//import AssemblyKeys._

name := "Hilda"

version := "0.0.15"

scalaVersion := "2.9.1"

scalacOptions += "-deprecation"

//seq(assemblySettings: _*)

seq(ProguardPlugin.proguardSettings :_*)

//mainClass in assembly := Some("com.ansvia.hilda.Hilda")

proguardOptions += keepMain("com.ansvia.hilda.Hilda")

resolvers ++= Seq(
  "Maven repo1" at "http://repo1.maven.org/maven2/",
  "Sonatype"    at "http://nexus.scala-tools.org/content/repositories/public",
  "Scala Tools" at "http://scala-tools.org/repo-snapshots/",
  "JBoss"       at "http://repository.jboss.org/nexus/content/groups/public/",
  "Akka"        at "http://akka.io/repository/",
  "GuiceyFruit" at "http://guiceyfruit.googlecode.com/svn/repo/releases/"
)

libraryDependencies ++= Seq(
	"jline" % "jline" % "1.0",
	"ch.qos.logback" % "logback-core" % "1.0.0",
	"ch.qos.logback" % "logback-classic" % "1.0.0",
	"commons-io" % "commons-io" % "2.1",
	"org.scala-lang" % "scala-swing" % "2.9.1"
)

