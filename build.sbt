import play.core.PlayVersion

def common: Seq[Setting[_]] = Seq(
  scalaVersion := "2.12.4",
  organization := "com.lightbend.lagom.microprofile",
  version := "1.0.0-alpha-jroper-1",
  publishMavenStyle := true,
  bintrayOrganization := Some("jroper"),
  bintrayRepository := "maven",
  bintrayPackage := "lagom-microprofile",
  licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")))
)

lazy val root = (project in file("."))
  .settings(common: _*)
  .aggregate(`play-cdi`, `lagom-cdi`, `lagom-cdi-server`, `lagom-cdi-persistence`, `lagom-reactive-messaging`, `lagom-persistence-messaging`)
  .settings(
    name := "lagom-microprofile",
    skip in publish := true
  )

lazy val `play-cdi` = (project in file("play-cdi"))
  .settings(common: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play" % PlayVersion.current,
      "org.jboss.weld.se" % "weld-se-core" % "3.0.4.Final"
    )
  )

lazy val `lagom-cdi` = (project in file("lagom-cdi"))
  .settings(common: _*)
  .dependsOn(`play-cdi`)
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslClient exclude ("com.typesafe.play", "play-guice")
    )
  )

lazy val `lagom-cdi-server` = (project in file("lagom-cdi-server"))
  .settings(common: _*)
  .dependsOn(`lagom-cdi`)
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslServer exclude ("com.typesafe.play", "play-guice")
    )
  )

lazy val `lagom-cdi-persistence` = (project in file("lagom-cdi-persistence"))
  .settings(common: _*)
  .dependsOn(`play-cdi`)
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslPersistence exclude ("com.typesafe.play", "play-guice")
    )
  )

lazy val `lagom-reactive-messaging` = (project in file("lagom-reactive-messaging"))
  .settings(common: _*)
  .dependsOn(`play-cdi`)
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslClient exclude ("com.typesafe.play", "play-guice"),
      lagomJavadslKafkaClient exclude ("com.typesafe.play", "play-guice"),
      "com.lightbend.microprofile.reactive.messaging" % "lightbend-microprofile-reactive-messaging-kafka" % "1.0.0-alpha-jroper-1",
      "org.glassfish" % "javax.json" % "1.1.2"
    )
  )

lazy val `lagom-persistence-messaging` = (project in file("lagom-persistence-messaging"))
  .settings(common: _*)
  .dependsOn(`lagom-reactive-messaging`)
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslPersistence exclude ("com.typesafe.play", "play-guice")
    )
  )

lazy val example = (project in file("example"))
  .dependsOn(`lagom-reactive-messaging`, `lagom-cdi-persistence`, `lagom-persistence-messaging`, `lagom-cdi-server`)
  .enablePlugins(LagomJava)
  .settings(common: _*)
  .settings(
    libraryDependencies -= lagomJavadslServer,
    libraryDependencies ++= Seq(
      lagomJavadslPersistenceCassandra exclude ("com.typesafe.play", "play-guice")
    )
  )

lagomKafkaEnabled in ThisBuild := true
lagomCassandraEnabled in ThisBuild := true

resolvers in ThisBuild ++= Seq(
  Resolver.bintrayRepo("jroper", "maven")
)