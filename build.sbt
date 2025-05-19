import sbt.*
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.*
import scoverage.ScoverageKeys

val appName = "tax-enrolment-assignment-frontend"

ThisBuild / majorVersion := 2
ThisBuild / scalaVersion := "3.3.5"
ThisBuild / scalafmtOnCompile := true

lazy val scoverageSettings: Seq[Setting[?]] = {
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;uk.gov.hmrc.taxenrolmentassignmentfrontend.views.*;uk.gov.hmrc.taxenrolmentassignmentfrontend.models.*;config.*;.*(BuildInfo|Routes).*;uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.*",
    ScoverageKeys.coverageMinimumStmtTotal := 89,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    PlayKeys.playDefaultPort := 7750,
    scoverageSettings,
    libraryDependencies ++= AppDependencies.all
  )
  .settings(
    scalacOptions ++= Seq(
      "-unchecked",
      "-feature",
      "-language:noAutoTupling",
      "-Werror",
      "-Wconf:msg=unused import&src=.*views/.*:s",
      "-Wconf:msg=unused import&src=<empty>:s",
      "-Wconf:msg=unused&src=.*RoutesPrefix\\.scala:s",
      "-Wconf:msg=unused&src=.*Routes\\.scala:s",
      "-Wconf:msg=unused&src=.*ReverseRoutes\\.scala:s",
      "-Wconf:msg=unused&src=.*JavaScriptReverseRoutes\\.scala:s",
      "-Wconf:msg=other-match-analysis:s",
      "-Wconf:msg=Flag.*repeatedly:s",
      "-Wconf:src=routes/.*:s",
      "-Wconf:msg=deprecation&src=views/.*:s" // should be removed after the UI is upgraded to use HmrcStandardPage
    )
  )
  .settings(routesImport ++= Seq("uk.gov.hmrc.play.bootstrap.binders.RedirectUrl"))


Test / Keys.fork := true
Test / parallelExecution := true
Test / scalacOptions --= Seq("-Wdead-code", "-Wvalue-discard")


lazy val it = project
  .enablePlugins(play.sbt.PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(
    libraryDependencies ++= AppDependencies.test,
    DefaultBuildSettings.itSettings()
  )

TwirlKeys.templateImports ++= Seq(
  "uk.gov.hmrc.govukfrontend.views.html.components._",
  "uk.gov.hmrc.hmrcfrontend.views.html.components._",
  "uk.gov.hmrc.hmrcfrontend.views.html.helpers._"
)

