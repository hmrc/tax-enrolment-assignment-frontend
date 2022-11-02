/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package helpers

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.TestITData._
import helpers.WiremockHelper.stubGet
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NON_AUTHORITATIVE_INFORMATION}
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo, UserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{routes, testOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersGroupResponse
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

trait TestHelper extends IntegrationSpecBase {


  val teaHost = s"localhost:$port"

  val appConfig = app.injector.instanceOf[AppConfig]

  val sessionCookie
    : (String, String) = ("COOKIE" -> createSessionCookieAsString(sessionData))

  val errorView = app.injector.instanceOf[ErrorTemplate]

  val exampleMongoSessionData = Map(ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER), REDIRECT_URL -> JsString("redirectURL"))

  def requestWithAccountType(
    accountType: AccountTypes.Value,
    redirectUrl: String = UrlPaths.returnUrl,
    mongoCacheData: Map[String, JsValue] = exampleMongoSessionData,
  ): RequestWithUserDetailsFromSessionAndMongo[_] =
    RequestWithUserDetailsFromSessionAndMongo(
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetailsNoEnrolments,
      sessionId,
      AccountDetailsFromMongo(accountType, redirectUrl, mongoCacheData)(crypto.crypto)
    )

  def requestWithUserDetails(userDetails: UserDetailsFromSession = userDetailsNoEnrolments): RequestWithUserDetailsFromSession[_] =
    RequestWithUserDetailsFromSession(
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetails,
      sessionId
    )

  def messagesApi: MessagesApi = {
    app.injector.instanceOf[MessagesApi]
  }
  implicit val messages: Messages = messagesApi.preferred(List(Lang("en")))

  object UrlPaths {
    val returnUrl: String = testOnly.routes.TestOnlyController.successfulCall
      .absoluteURL(false, teaHost)
    val accountCheckPath =
      routes.AccountCheckController.accountCheck(RedirectUrl.apply(returnUrl)).url
    val enrolledPTNoSAOnAnyAccountPath    = routes.EnrolledForPTController.view.url
    val enrolledPTWithSAOnAnyAccountPath  = routes.EnrolledForPTWithSAController.view.url
    val enrolledPTSAOnOtherAccountPath =
      routes.EnrolledPTWithSAOnOtherAccountController.view.url
    val reportFraudPTAccountPath =
      routes.ReportSuspiciousIDController.viewNoSA.url
    val reportFraudSAAccountPath =
      routes.ReportSuspiciousIDController.viewSA.url
    val saOnOtherAccountInterruptPath =
      routes.SABlueInterruptController.view.url
    val saOnOtherAccountKeepAccessToSAPath =
      routes.KeepAccessToSAController.view.url
    val saOnOtherAccountSigninAgainPath =
      routes.SignInWithSAAccountController.view.url
    val enrolForSAPath = routes.EnrolForSAController.enrolForSA.url
    val ptOnOtherAccountPath =
      routes.PTEnrolmentOnOtherAccountController.view.url
    val logoutPath = routes.SignOutController.signOut.url
    val unauthorizedPath = routes.AuthorisationController.notAuthorised.url
    val keepAlive = routes.TimeOutController.keepAlive.url
    val timeout = routes.TimeOutController.timeout.url
    val signout = routes.SignOutController.signOut().url
  }

  def stubUserGroupSearchSuccess(
                                  credId: String,
                                  usersGroupResponse: UsersGroupResponse
                                ): StubMapping = stubGet(
    s"/users-groups-search/users/$credId",
    NON_AUTHORITATIVE_INFORMATION,
    usergroupsResponseJson(usersGroupResponse).toString()
  )

  def stubUserGroupSearchFailure(
                                  credId: String,
                                  responseCode: Int = INTERNAL_SERVER_ERROR
                                ): StubMapping =
    stubGet(s"/users-groups-search/users/$credId", INTERNAL_SERVER_ERROR, "")

}
