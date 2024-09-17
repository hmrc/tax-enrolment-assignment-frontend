import sbt.*
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.*

val appName = "tax-enrolment-assignment-frontend"

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / scalafmtOnCompile := true

lazy val scoverageSettings: Seq[Setting[?]] = {
  import scoverage.ScoverageKeys
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;models/.data/..*;view.*;models.*;config.*;.*(BuildInfo|Routes).*;controllers.testOnly.*;uk.gov.hmrc.taxenrolmentassignmentfrontend.models.RichJsValue.scala;uk.gov.hmrc.taxenrolmentassignmentfrontend.models.RichJsObject.scala;uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.Page.scala",
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
    scalaSettings,
    libraryDependencies ++= AppDependencies.all
  )
  .settings(
    scalacOptions ++= Seq(
      "-unchecked",
      "-feature",
      "-Wdead-code",
      "-Wunused:_",
      "-Wextra-implicit",
      "-Ywarn-unused",
      "-Werror",
      "-Wconf:cat=unused-imports&site=.*views\\.html.*:s",
      "-Wconf:cat=unused-imports&site=<empty>:s",
      "-Wconf:cat=unused&src=.*RoutesPrefix\\.scala:s",
      "-Wconf:cat=unused&src=.*ErrorHandler\\.scala:s",
      "-Wconf:cat=unused&src=.*Routes\\.scala:s",
      "-Wconf:cat=unused&src=.*ReverseRoutes\\.scala:s",
      "-Wconf:cat=deprecation&src=views/.*:s", // should be removed after the UI is upgraded to use HmrcStandardPage
      "-Wconf:src=test/.*&msg=a type was inferred to be `Object`:s", // silence warnings from mockito reset
      "-Wconf:msg=\\.*match may not be exhaustive.\\.*:s"
    )
  )
  .settings(scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "off"))
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

