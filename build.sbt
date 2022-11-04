name := "scala-utils"

scalaVersion := "2.13.10"

// Ensure that the versions below are mutually compatible - else unexpected errors will happen.
val akkaVersion = "2.7.0"
val akkaHttpVersion = "10.4.0"

resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases"
resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  // Logging
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  , "com.typesafe.akka" %% "akka-stream" % akkaVersion
//  ,"org.slf4j" % "slf4j-simple" % "1.7.25"

  // JSON processing.
  ,"de.heikoseeberger" %% "akka-http-json4s" % "1.40.0-RC3"
  ,"org.json4s" %% "json4s-native" % "4.1.0-M2"

  ,"com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion  // The Akka HTTP client.
)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.14" % "test"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"


publishMavenStyle := true
publishTo := sonatypePublishToBundle.value

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommand("publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeRelease"),
  pushChanges
)
