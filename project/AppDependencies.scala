import play.core.PlayVersion.current

import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-frontend-play-28" % "5.20.0",
    "uk.gov.hmrc" %% "play-frontend-hmrc" % "2.0.0-play-28",
    "uk.gov.hmrc" %% "auth-client" % "5.8.0-play-28",
    "org.typelevel" %% "cats-core" % "2.7.0",
    "uk.gov.hmrc" %% "domain" % "6.2.0-play-28"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-28" % "5.20.0" % Test,
    "org.scalatest" %% "scalatest" % "3.2.10" % "test, it",
    "com.typesafe.play" %% "play-test" % current % Test,
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % "test, it",
    "org.jsoup" % "jsoup" % "1.14.3" % Test,
    "com.vladsch.flexmark" % "flexmark-all" % "0.62.2" % "test, it",
    "org.scalamock" %% "scalamock" % "5.2.0" % "test, it",
    "com.github.tomakehurst" % "wiremock-jre8-standalone" % "2.32.0" % "test, it"
  )
}
