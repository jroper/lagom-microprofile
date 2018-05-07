def common: Seq[Setting[_]] = Seq(
  scalaVersion := "2.12.4"
)

lazy val root = (project in file("."))
  .settings(common: _*)
  .aggregate(`play-cdi`)

lazy val `play-cdi` = (project in file("play-cdi"))
  .settings(common: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play" % "2.6.13",
      "org.jboss.weld.se" % "weld-se-core" % "3.0.4.Final"
    )
  )

lazy val example = (project in file("example"))
  .dependsOn(`play-cdi`)
  .enablePlugins(PlayJava)
  .disablePlugins(PlayLayoutPlugin)
  .settings(common: _*)
