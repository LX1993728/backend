name := """ticket"""

version := "3.5.5-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
    javaJpa,
    "org.hibernate" % "hibernate-entitymanager" % "4.2.15.Final",
    "postgresql" % "postgresql" % "9.1-901.jdbc4",
    "com.fasterxml.jackson.core" % "jackson-core" % "2.3.1",
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.3.3",
    "com.fasterxml.jackson.core" % "jackson-annotations" % "2.3.0",
    "org.apache.httpcomponents" % "httpclient" % "4.3.5",
    "org.apache.httpcomponents" % "fluent-hc" % "4.2.6",
    "org.mindrot" % "jbcrypt" % "0.3m",
    "org.apache.commons" % "commons-email" % "1.4")

