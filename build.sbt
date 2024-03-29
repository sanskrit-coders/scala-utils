name := "scala-utils"
ThisBuild / versionScheme := Some("strict")


scalaVersion := "2.13.10"

// Ensure that the versions below are mutually compatible - else unexpected errors will happen.
val akkaVersion = "2.7.0"
val akkaHttpVersion = "10.4.0"

resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
resolvers +=
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases"
resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  // Logging
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  , "com.typesafe.akka" %% "akka-stream" % akkaVersion
//  ,"org.slf4j" % "slf4j-simple" % "1.7.25"

  ,"com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion  // The Akka HTTP client.
)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.14" % "test"


assembly / assemblyOutputPath := file("bin/artifacts/scala-utils.jar")
//assembly / mainClass := Some("stardict_sanskrit.commandInterface")



publishMavenStyle := true
publishTo := sonatypePublishToBundle.value

import ReleaseTransformations._

// releaseCrossBuild := true // true if you cross-build the project for multiple Scala versions
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  releaseStepCommand("assembly"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommand("publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
