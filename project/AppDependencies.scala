import sbt._

object AppDependencies {

  val bootstrapVersion = "8.5.0"
  val hmrcMongoVersion = "1.7.0"
  val playVersion = "play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVersion"  % bootstrapVersion,
    "uk.gov.hmrc"       %% s"play-frontend-hmrc-$playVersion"  % "9.0.0",
    "org.typelevel"     %% "cats-core"                         % "2.12.0",
    "uk.gov.hmrc"       %% s"domain-$playVersion"              % "9.0.0",
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"          % hmrcMongoVersion,
    "uk.gov.hmrc"   %% s"crypto-json-$playVersion"                  % "7.6.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.mockito"       %% "mockito-scala-scalatest"       % "1.17.37",
    "org.scalatest"     %% "scalatest"                     % "3.2.19",
    "org.scalatestplus" %% "scalacheck-1-17"               % "3.2.18.0",
    "org.jsoup"          % "jsoup"                         % "1.18.1",
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test
}
