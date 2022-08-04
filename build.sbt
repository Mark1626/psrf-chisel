// See README.md for license details.

ThisBuild / scalaVersion := "2.12.13"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "com.thoughtworks"

val chiselVersion = "3.5.1"

lazy val root = (project in file("."))
  .settings(
    name := "psrf-chisel",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3"       % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest"    % "0.5.1" % "test",
      "io.circe"        %% "circe-core"    % "0.14.1",
      "io.circe"        %% "circe-generic" % "0.14.1",
      "io.circe"        %% "circe-parser"  % "0.14.1",
      "me.sequencer"     % "cde_2.12"      % "v0.1.0-17-fcde29"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-P:chiselplugin:genBundleElements"
    ),
    addCompilerPlugin(("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion).cross(CrossVersion.full))
  )
