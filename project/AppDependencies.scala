import play.core.PlayVersion.current

import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"         %%  "bootstrap-frontend-play-28"  % "6.3.0",
    "uk.gov.hmrc"         %%  "play-frontend-hmrc"          % "3.15.0-play-28",
    "org.typelevel"       %%  "cats-core"                   % "2.7.0",
    "uk.gov.hmrc"         %%  "domain"                      % "8.0.0-play-28",
    "uk.gov.hmrc"         %%  "http-caching-client"         % "9.6.0-play-28",
    "uk.gov.hmrc.mongo"   %%  "hmrc-mongo-play-28"          % "0.63.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %%  "bootstrap-test-play-28"    %  "6.3.0"  % Test,
    "org.scalatest"           %%  "scalatest"                 %  "3.2.12"  % "test, it",
    "com.typesafe.play"       %%  "play-test"                 %  current   % Test,
    "org.scalatestplus.play"  %%  "scalatestplus-play"        %  "5.1.0"   % "test, it",
    "org.jsoup"               %   "jsoup"                     %  "1.14.3"  % "test, it",
    "com.vladsch.flexmark"    %   "flexmark-profile-pegdown"  %  "0.62.2"  % "test, it",
    "org.scalamock"           %%  "scalamock"                 %  "5.2.0"   % "test, it",
    "com.github.tomakehurst"  %   "wiremock-jre8-standalone"  %  "2.33.1"  % "test, it"
  )
}
