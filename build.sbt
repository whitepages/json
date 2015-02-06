name := "persist-json"

organization := "com.persist"

version := "0.21-SNAPSHOT"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "com.chuusai" %% "shapeless" % "2.1.0-RC2",
        "org.scalatest" %% "scalatest" % "2.2.4" % "test",
        "org.specs2" %% "specs2-core" % "2.4.16" % "test",
        "junit" % "junit" % "4.12" % "test"
)

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo)

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

homepage := Some(url("https://github.com/nestorpersist/json"))

scmInfo := Some(ScmInfo(url("https://github.com/nestorpersist/json"), "scm:git@github.com:nestorpersist/json.git"))

pomExtra := (
  <developers>
    <developer>
      <id>johnnestor</id>
      <name>John Nestor</name>
      <email>nestor@persist.com</email>
      <url>http://http://www.persist.com</url>
    </developer>
    <developer>
      <id>jedesah</id>
      <name>Jean-Remi Desjardins</name>
      <email>jeanremi.desjardins@gmail.com</email>
      <url>https://github.com/jedesah</url>
    </developer>
  </developers>
)