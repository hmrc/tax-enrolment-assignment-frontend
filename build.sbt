import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "tax-enrolment-assignment-frontend"

val silencerVersion = "1.7.8"

lazy val coverageSettings: Seq[Setting[_]] = {
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;models/.data/..*;view.*;models.*;config.*;.*(BuildInfo|Routes).*;controllers.testOnly.*",
    ScoverageKeys.coverageMinimumStmtTotal := 77,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val scalaStyleSettings = {
  Seq(scalastyleFailOnError := true)
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion := 0,
    scalaVersion := "2.12.15",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    PlayKeys.playDefaultPort := 7750,
    pipelineStages in Assets := Seq(gzip),
    // ***************
    // Use the silencer plugin to suppress warnings
    scalacOptions += "-P:silencer:pathFilters=routes",
    libraryDependencies ++= Seq(
      caffeine,
      compilerPlugin(
        "com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full
      ),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
    // ***************
  )
  .settings(coverageSettings: _*)
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
