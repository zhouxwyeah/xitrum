organization := "tv.cntt"

name := "xitrum"

version := "3.22-SNAPSHOT"

scalaVersion := "2.11.4"
//scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.11.4", "2.10.4")

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

// xitrum.util.FileMonitor requires Java 7
javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

// Source code for Scala 2.10 and 2.11 are a little different ------------------
// See src/main/scala-2.10 and src/main/scala-2.11.

unmanagedSourceDirectories in Compile += baseDirectory.value / "src" / "main" / s"scala-${scalaBinaryVersion.value}"

// Generate src/main/scala/xitrum/Version.scala from "version" above -----------

val generateVersionFileTask = TaskKey[Unit]("generateVersion", "Generate src/main/scala/xitrum/Version.scala")

generateVersionFileTask <<= generateVersionFile

(compile in Compile) <<= (compile in Compile) dependsOn (generateVersionFile)

def generateVersionFile = Def.task {
  val versions = version.value.split('.')
  val major    = versions(0).toInt
  val minor    = versions(1).split('-')(0).toInt
  val ma_mi    = s"$major.$minor"
  val base     = (baseDirectory in Compile).value

  // Also check if the directory name is correct
  val resDir = base / s"src/main/resources/META-INF/resources/webjars/xitrum/$ma_mi"
  if (!resDir.exists) throw new Exception(s"Directory name incorrect: $resDir")

  // Do not overwrite version file if its content doesn't change
  val file    = base / "src/main/scala/xitrum/Version.scala"
  val content = s"""// Autogenerated by build.sbt. Do not modify this file directly.
package xitrum
class Version {
  val major = $major
  val minor = $minor
  /** major.minor */
  override def toString = "$ma_mi"
}
"""
  if (!file.exists) {
    IO.write(file, content)
  } else {
    val oldContent = IO.read(file)
    if (content != oldContent) IO.write(file, content)
  }
}

//------------------------------------------------------------------------------

// Projects using Xitrum must provide a concrete implementation of SLF4J (Logback etc.)
libraryDependencies += "org.slf4s" %% "slf4s-api" % "1.7.7"

// Netty is the core of Xitrum's HTTP(S) feature
libraryDependencies += "io.netty" % "netty-all" % "4.0.25.Final"

// http://netty.io/wiki/forked-tomcat-native.html
// Include all classifiers for convenience
libraryDependencies += "io.netty" % "netty-tcnative" % "1.1.32.Fork1" classifier "linux-x86_64"

libraryDependencies += "io.netty" % "netty-tcnative" % "1.1.32.Fork1" classifier "osx-x86_64"

libraryDependencies += "io.netty" % "netty-tcnative" % "1.1.32.Fork1" classifier "windows-x86_64"

// Javassist boosts Netty 4 speed
libraryDependencies += "org.javassist" % "javassist" % "3.18.2-GA"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.8"

libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.3.8"

libraryDependencies += "com.typesafe.akka" %% "akka-contrib" % "2.3.8"

// Redirect Akka log to SLF4J
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.3.8"

// For clustering SockJS with Akka
libraryDependencies += "tv.cntt" %% "glokka" % "2.2"

// For file watch
// (akka-agent is added here, should ensure same Akka version as above)
libraryDependencies += "com.beachape.filemanagement" %% "schwatcher" % "0.1.5"

// For scanning routes
libraryDependencies += "tv.cntt" %% "sclasner" % "1.6"

// For binary (de)serializing
libraryDependencies += "com.twitter" %% "chill" % "0.5.1"

// For JSON (de)serializing
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.11"

// For i18n
libraryDependencies += "tv.cntt" %% "scaposer" % "1.5"

// For jsEscape
libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.3.2"

// For compiling CoffeeScript to JavaScript
libraryDependencies += "tv.cntt" % "rhinocoffeescript" % "1.8.0"

// For metrics
libraryDependencies += "nl.grons" %% "metrics-scala" % "3.3.0_a2.3"

// For metrics
libraryDependencies += "io.dropwizard.metrics" % "metrics-json" % "3.1.0"

// JSON4S uses scalap 2.10.0/2.11.0, which in turn uses scala-compiler 2.10.0/2.11.0, which in
// turn uses scala-reflect 2.10.0/2.11.0. We need to force "scalaVersion" above, because
// Scala annotations (used by routes and Swagger) compiled by a newer version
// can't be read by an older version.
//
// Also, we must release a new version of Xitrum every time a new version of
// Scala is released.
libraryDependencies <+= scalaVersion { sv => "org.scala-lang" % "scalap" % sv }

// WebJars ---------------------------------------------------------------------

libraryDependencies += "org.webjars" % "jquery" % "2.1.3"

libraryDependencies += "org.webjars" % "jquery-validation" % "1.13.1"

libraryDependencies += "org.webjars" % "sockjs-client" % "0.3.4"

libraryDependencies += "org.webjars" % "swagger-ui" % "2.0.24"

libraryDependencies += "org.webjars" % "d3js" % "3.5.2"

// For test --------------------------------------------------------------------

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % "test"

// An implementation of SLF4J is needed for log in tests to be output
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2" % "test"

// For "sbt console"
unmanagedClasspath in Compile <+= (baseDirectory) map { bd => Attributed.blank(bd / "src/test/resources") }

// For "sbt run/test"
unmanagedClasspath in Runtime <+= (baseDirectory) map { bd => Attributed.blank(bd / "src/test/resources") }

//------------------------------------------------------------------------------

// Avoid messy Scaladoc by excluding things that are not intended to be used
// directly by normal Xitrum users.
scalacOptions in (Compile, doc) ++= Seq("-skip-packages", "xitrum.sockjs")

// Skip API doc generation to speedup "publish-local" while developing.
// Comment out this line when publishing to Sonatype.
publishArtifact in (Compile, packageDoc) := false
