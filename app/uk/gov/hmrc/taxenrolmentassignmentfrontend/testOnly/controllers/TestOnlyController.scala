/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.controllers

import cats.implicits.toTraverseOps
import play.api.libs.json.{JsArray, JsResultException, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UpstreamError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.{BasStubsConnectorTestOnly, EnrolmentStoreConnectorTestOnly, EnrolmentStoreStubConnectorTestOnly, IdentityVerificationConnectorTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.AccountDetailsTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.services.{EnrolmentStoreServiceTestOnly, EnrolmentStoreStubServiceTestOnly}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class TestOnlyController @Inject() (
  enrolmentStoreStubConnectorTestOnly: EnrolmentStoreStubConnectorTestOnly,
  enrolmentStoreStubServiceTestOnly: EnrolmentStoreStubServiceTestOnly,
  enrolmentStoreConnectorTestOnly: EnrolmentStoreConnectorTestOnly,
  enrolmentStoreServiceTestOnly: EnrolmentStoreServiceTestOnly,
  identityVerificationConnectorTestOnly: IdentityVerificationConnectorTestOnly,
  basStubsConnectorTestOnly: BasStubsConnectorTestOnly,
  mcc: MessagesControllerComponents,
  authAction: AuthAction,
  logger: EventLoggerService
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def create: Action[AnyContent] = Action.async { request =>
    implicit val hc = HeaderCarrier(Some(Authorization("Bearer 1")))

    val jsonData = request.body.asJson.get

    val accountList = Try(jsonData.as[JsArray]) match {
      case Success(_)                    => jsonData.as[List[AccountDetailsTestOnly]]
      case Failure(_: JsResultException) => List(jsonData.as[AccountDetailsTestOnly])
      case Failure(error)                => throw error
    }

    accountList
      .map { data =>
        for {
          // delete bas-stub data
          _ <- basStubsConnectorTestOnly.deleteAdditionalFactors(data.user.credId)
          // delete identity-verification data
          _ <- identityVerificationConnectorTestOnly.deleteCredId(data.user.credId)
          // delete enrolment-store data
          _ <- data.enrolments.map(enrolmentStoreServiceTestOnly.deallocateEnrolmentFromGroups(_)).sequence
          _ <- data.enrolments.map(enrolmentStoreServiceTestOnly.deallocateEnrolmentFromUsers(_)).sequence
          _ <- data.enrolments.map(enrolmentStoreServiceTestOnly.deleteEnrolment(_)).sequence
          _ <- enrolmentStoreStubServiceTestOnly.deleteAccountIfExist(data.groupId)
          _ <- enrolmentStoreServiceTestOnly.deallocateEnrolmentsFromGroup(data.groupId)
          _ <- enrolmentStoreServiceTestOnly.deallocateEnrolmentsFromUser(data.user.credId)

          // Insert enrolment-store data
          _ <- enrolmentStoreStubConnectorTestOnly
                 .addStubAccount(data)
          _ <- data.enrolments.map(enrolment => enrolmentStoreConnectorTestOnly.upsertEnrolment(enrolment)).sequence
          _ <-
            data.enrolments
              .map(enrolment =>
                enrolmentStoreConnectorTestOnly.addEnrolmentToGroup(data.groupId, data.user.credId, enrolment)
              )
              .sequence
          // insert identity-verification data
          _ <- identityVerificationConnectorTestOnly.insertCredId(data.user.credId, data.nino)
          // insert bas-stubs data
          _ <- basStubsConnectorTestOnly.putAccount(data)
          _ <- basStubsConnectorTestOnly.putAdditionalFactors(data)
        } yield ()
      }
      .sequence
      .fold(
        {
          case UpstreamError(error) => InternalServerError(error.message)
          case error                => InternalServerError(error.toString)
        },
        _ => Ok("Created")
      )
  }

  def successfulCall: Action[AnyContent] = Action.async { _ =>
    logger.logEvent(logSuccessfulRedirectToReturnUrl)
    Future.successful(Ok("Successful"))
  }

  def usersGroupSearchCall(credId: String): Action[AnyContent] = Action.async { _ =>
    val userDetails =
      UsersGroupsFixedData.usersGroupSearchCreds.getOrElse(credId, UsersGroupsFixedData.defaultUserResponse)
    Future.successful(
      NonAuthoritativeInformation(
        UsersGroupsFixedData.toJson(userDetails)
      )
    )
  }

  def enrolmentsFromAuth(): Action[AnyContent] = authAction { implicit request =>
    Ok(Json.toJson(request.userDetails.enrolments.enrolments)(EnrolmentsFormats.writes).toString())
  }

  val successfulSACall: Action[AnyContent] = Action.async { _ =>
    Future.successful(Ok("Successful Redirect to SA"))
  }

  val authStub: Action[AnyContent] = Action { _ =>
    Ok
  }

  val taxEnrolmentsStub: Action[AnyContent] = Action { _ =>
    NoContent
  }
}
