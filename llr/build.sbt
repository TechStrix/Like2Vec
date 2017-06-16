import sbtassembly.{MergeStrategy, PathList}

name := "llr"

version := "1.0"

scalaVersion := "2.10.6"

resolvers++=Seq("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "releases" at "http://oss.sonatype.org/service/local/staging/deploy/maven2/")

resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"

libraryDependencies += "org.apache.spark" % "spark-core_2.10" % "2.1.0"
//libraryDependencies += "org.apache.spark" %% "spark-mllib_2.10" % "2.1.0"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.2.0"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"


// grading libraries
libraryDependencies += "junit" % "junit" % "4.10" % "test"
//libraryDependencies ++= assignmentsMap.value.values.flatMap(_.dependencies).toSeq



assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}