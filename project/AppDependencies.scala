import play.core.PlayVersion.current

import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-frontend-play-28" % "5.17.0",
    "uk.gov.hmrc"             %% "play-frontend-hmrc"         % "1.26.0-play-28",
    "uk.gov.hmrc"             %% "auth-client"                % "5.7.0-play-28"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % "5.17.0"          % Test,
    "org.scalatest"           %% "scalatest"                 % "3.2.5"            % "test, it",
    "com.typesafe.play"        %% "play-test"                  % current          % Test,
    "org.jsoup"               %  "jsoup"                      % "1.13.1"          % Test,
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.36.8"            % "test, it",
    "org.scalamock"            %% "scalamock"                  % "5.1.0"     % "test, it",
    "com.github.tomakehurst"   % "wiremock-jre8-standalone"    % "2.31.0"    % "test, it"
  )
}
