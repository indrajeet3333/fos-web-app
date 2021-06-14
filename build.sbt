name := """FOS"""
organization := "abc"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.1"

libraryDependencies += jdbc
libraryDependencies += guice
libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.16"
libraryDependencies += "org.postgresql" % "postgresql" % "9.4-1200-jdbc41"
