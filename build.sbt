name := "SpinalTemplateSbt"

version := "1.0"

scalaVersion := "2.11.12"

EclipseKeys.withSource := true

libraryDependencies ++= Seq(
  "com.github.spinalhdl" % "spinalhdl-core_2.11" % "1.3.5",
  "com.github.spinalhdl" % "spinalhdl-lib_2.11" % "1.3.5"
)

val circeVersion = "0.11.1"
libraryDependencies += "io.circe" %% "circe-core" % circeVersion
libraryDependencies += "io.circe" %% "circe-generic" % circeVersion
libraryDependencies += "io.circe" %% "circe-parser"% circeVersion
libraryDependencies += "io.circe" %% "circe-yaml" % "0.10.0"

fork := true
