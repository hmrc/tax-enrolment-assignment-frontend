import AppDependencies.playVersion
import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  private val playVersion = "play-28"
  private val bootstrapVersion = "7.14.0"
  private val hmrcMongoVersion = "0.73.0"

  val compile = Seq(
    "uk.gov.hmrc"         %%  s"bootstrap-frontend-$playVersion"  % bootstrapVersion,
    "uk.gov.hmrc"         %%  "play-frontend-hmrc"          % s"3.29.0-$playVersion",
    "org.typelevel"       %%  "cats-core"                   % "2.8.0",
    "uk.gov.hmrc"         %%  "domain"                      % s"8.1.0-$playVersion",
    "uk.gov.hmrc"         %%  "http-caching-client"         % s"9.6.0-$playVersion",
    "uk.gov.hmrc.mongo"   %%  s"hmrc-mongo-$playVersion"          % hmrcMongoVersion,
    "uk.gov.hmrc"         %%  s"crypto-json-$playVersion"         % "7.2.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %%  s"bootstrap-test-$playVersion"    %  bootstrapVersion  % Test,
    "org.scalatest"           %%  "scalatest"                 %  "3.2.12"  % "test, it",
    "com.typesafe.play"       %%  "play-test"                 %  current   % Test,
    "org.scalatestplus.play"  %%  "scalatestplus-play"        %  "5.1.0"   % "test, it",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion,
    "org.jsoup"               %   "jsoup"                     %  "1.15.2"  % "test, it",
    "com.vladsch.flexmark"    %   "flexmark-profile-pegdown"  %  "0.62.2"  % "test, it",
    "org.scalamock"           %%  "scalamock"                 %  "5.2.0"   % "test, it",
    "com.github.tomakehurst"  %   "wiremock-jre8-standalone"  %  "2.33.2"  % "test, it"
  )
}
