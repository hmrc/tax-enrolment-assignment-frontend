import sbt._

object AppDependencies {

  val bootstrapVersion = "7.15.0"
  val hmrcMongoVersion = "1.1.0"
  val playVersion = "play-28"

  val compile = Seq(
    "uk.gov.hmrc"         %%  s"bootstrap-frontend-$playVersion"  % bootstrapVersion,
    "uk.gov.hmrc"         %%  "play-frontend-hmrc"          % s"7.19.0-$playVersion",
    "org.typelevel"       %%  "cats-core"                   % "2.9.0",
    "uk.gov.hmrc"         %%  "domain"                      % s"8.1.0-$playVersion",
    "uk.gov.hmrc"         %%  "http-caching-client"         % s"9.6.0-$playVersion",
    "uk.gov.hmrc.mongo"   %%  s"hmrc-mongo-$playVersion"          % hmrcMongoVersion,
    "uk.gov.hmrc"         %%  s"crypto-json-$playVersion"         % "7.2.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %%  "bootstrap-test-play-28"    %  bootstrapVersion,
    "org.mockito"             %% "mockito-scala-scalatest"       % "1.17.14",
    "org.scalatest"           %%  "scalatest"                 %  "3.2.15",
    "org.jsoup"               %   "jsoup"                     %  "1.15.4",
    "com.vladsch.flexmark"    %   "flexmark-profile-pegdown"  %  "0.64.0",
    "org.scalamock"           %%  "scalamock"                 %  "5.2.0",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_  % "test, it")
}
