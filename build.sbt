import play.core.PlayVersion

def common: Seq[Setting[_]] = Seq(
  scalaVersion := "2.12.4"
)

lazy val root = (project in file("."))
  .settings(common: _*)
  .aggregate(`play-cdi`, `lagom-cdi`, example)
  .settings(
    name := "lagom-microprofile"
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
      lagomJavadslServer exclude ("com.typesafe.play", "play-guice")
    )
  )

lazy val example = (project in file("example"))
  .dependsOn(`lagom-cdi`)
  .enablePlugins(LagomJava)
  .settings(common: _*)
  .settings(
    libraryDependencies -= lagomJavadslServer
  )

lagomKafkaEnabled in ThisBuild := false
lagomCassandraEnabled in ThisBuild := false