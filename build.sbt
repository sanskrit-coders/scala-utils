name := "scala-utils"

val akkaVersion = "2.5.4"
val akkaHttpVersion = "10.0.10"

resolvers += Resolver.sonatypeRepo("releases")
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  // Logging
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  ,"org.slf4j" % "slf4j-simple" % "1.7.25"

  // JSON processing.
  ,"de.heikoseeberger" %% "akka-http-json4s" % "1.19.0-M2"
  ,"org.json4s" % "json4s-native_2.12" % "3.5.3"

  ,"com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion  // The Akka HTTP client.

)

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.4"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

scmInfo := Some(
  ScmInfo(
    url("https://github.com/sanskrit-coders/scala-utils"),
    "scm:git@github.com:sanskrit-coders/scala-utils.git"
  )
)

useGpg := true
publishMavenStyle := true
publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

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
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
