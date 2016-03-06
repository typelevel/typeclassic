import ReleaseTransformations._

lazy val typeclassicSettings = Seq(
  organization := "org.typelevel",
  licenses := Seq(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))),
  homepage := Some(url("http://github.com/typelevel/typeclassic")),

  scalaVersion := "2.11.7",
  crossScalaVersions := Seq("2.10.6", "2.11.7"),

  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked"
  ),
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.typelevel" %% "macro-compat" % "1.1.1",
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  ),
  compileOrder := CompileOrder.JavaThenScala,
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := Function.const(false),

  publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging),

  pomExtra := (
    <scm>
      <url>git@github.com:typelevel/typeclassic.git</url>
      <connection>scm:git:git@github.com:typelevel/typeclassic.git</connection>
    </scm>
    <developers>
      <developer>
        <id>mpilquist</id>
        <name>Michael Pilquist</name>
        <url>http://github.com/mpilquist/</url>
      </developer>
      <developer>
        <id>milessabin</id>
        <name>Miles Sabin</name>
        <url>http://github.com/milessabin/</url>
      </developer>
      <developer>
        <id>d_m</id>
        <name>Erik Osheim</name>
        <url>http://github.com/non/</url>
      </developer>
      <developer>
        <id>tixxit</id>
        <name>Tom Switzer</name>
        <url>http://github.com/tixxit/</url>
      </developer>
    </developers>
  ),

  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
    pushChanges))

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false)

lazy val root = project
  .in(file("."))
  .aggregate(typeclassicJS, typeclassicJVM)
  .settings(name := "typeclassic-root")
  .settings(typeclassicSettings)
  .settings(noPublish)

lazy val typeclassic = crossProject
  .crossType(CrossType.Pure)
  .in(file("."))
  .settings(name := "typeclassic")
  .settings(typeclassicSettings: _*)

lazy val typeclassicJVM = typeclassic.jvm

lazy val typeclassicJS = typeclassic.js
