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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly

import cats.data.EitherT
import cats.implicits.toTraverseOps
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.retrieve.{EmptyRetrieval, ~}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{allEnrolments, credentials, nino}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequestAndSession
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.EACDConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.testOnly.{AccountConnector, EACDStubAddData, EnrolmentStoreConnector, EnrolmentStoreStubConnector, TaxEnrolmentsConnectorTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UpstreamError}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.testOnly.AccountDescription
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.testOnly.AccountService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlyController @Inject() (
  val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  authAction: AuthAction,
  logger: EventLoggerService,
  eacdConnect: EACDConnector,
  enrolmentStoreConnector: EnrolmentStoreConnector,
  taxEnrolmentsConnectorTestOnly: TaxEnrolmentsConnectorTestOnly,
  enrolmentStoreStubConnector: EnrolmentStoreStubConnector,
  accountService: AccountService,
  accountConnector: AccountConnector
)(implicit val executionContext: ExecutionContext)
    extends TEAFrontendController(mcc) with AuthorisedFunctions {

  def updateOrCreateAccount: Action[AnyContent] = Action.async { implicit request =>
    val requestBody = request.body.asJson.map(_.as[AccountDescription]).get

    accountService
      .accountSetup(requestBody)
      .map {
        case Right(_)    => Ok("All done")
        case Left(error) => InternalServerError("Failed to create account " + error)
      }
      .recover { case ex: Exception =>
        InternalServerError("Failed to create account " + ex)
      }
  }

  def repairAccount: Action[AnyContent] = Action.async { request =>
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequest(request)

    authorised().retrieve(EmptyRetrieval) { _ =>
      val requestBody = request.body.asJson.map(_.as[AccountDescription]).get

      def createIfMissing: TEAFResult[Unit] = enrolmentStoreStubConnector
        .getStubAccount(requestBody.groupId)
        .transform {
          case Right(account)                                                                    => Right(account.users.exists(_.credId == requestBody.credId))
          case Left(error: UpstreamError) if error.upstreamErrorResponse.statusCode == NOT_FOUND => Right(false)
          case Left(error)                                                                       => Left(error)
        }
        .map { isAccount =>
          if (!isAccount) {
            enrolmentStoreStubConnector.addStubAccount(EACDStubAddData(requestBody.groupId, requestBody.credId))
          } else {
            EitherT.rightT(()): TEAFResult[Unit]
          }
        }
        .flatMap(identity)

      val deletedHMRCPTEnrolment: TEAFResult[Unit] = (for {
        _               <- createIfMissing
        groupEnrolments <- enrolmentStoreConnector.getEnrolmentsFromGroupES3(requestBody.groupId)
        userEnrolments  <- eacdConnect.queryEnrolmentsAssignedToUser(requestBody.credId)
      } yield {
        val deletedFromGroup = groupEnrolments.enrolments
          .map { enrolment =>
            val key = s"${enrolment.service}~${enrolment.identifiers.head.key}~${enrolment.identifiers.head.value}"
            eacdConnect
              .deallocateEnrolment(requestBody.groupId, key)
              .bimap(error => UpstreamError(error): TaxEnrolmentAssignmentErrors, _ => ())
          }
          .sequence
          .map(_ => ())

        val deletedFromUser: Option[TEAFResult[Unit]] = userEnrolments.map { enrolments =>
          enrolments.enrolments
            .map { enrolment =>
              val key = s"${enrolment.service}~${enrolment.identifiers.head.key}~${enrolment.identifiers.head.value}"
              taxEnrolmentsConnectorTestOnly.deleteEnrolmentFromUserES12(requestBody.groupId, key)
            }
            .sequence
            .map(_ => ())
        }
        deletedFromUser.getOrElse(deletedFromGroup)
      }).flatMap(identity)

      deletedHMRCPTEnrolment
        .flatMap { _ =>
          requestBody.groupEnrolments
            .map { enrolment =>
              for {
                _ <- taxEnrolmentsConnectorTestOnly.upsertKnownFactES6(enrolment)
                _ <- taxEnrolmentsConnectorTestOnly.assignGroupSimpleEnrolment(
                       requestBody.credId,
                       requestBody.groupId,
                       enrolment
                     )
              } yield ()
            }
            .sequence
            .map(_ => ())
        }
        .fold(
          error => InternalServerError(error.toString),
          _ => Ok("Successful")
        )
    }
  }

  def successfulCall: Action[AnyContent] = Action.async { request =>
    val sessionHc: HeaderCarrier = fromRequestAndSession(request, request.session)
    val token = HeaderCarrierConverter.fromRequest(request).authorization

    implicit val hc: HeaderCarrier = if (sessionHc.authorization.isEmpty) {
      sessionHc.copy(authorization = token)
    } else sessionHc

    authorised().retrieve(nino and allEnrolments and credentials) {
      case Some(nino) ~ enrolments ~ credentials =>
        eacdConnect.queryEnrolmentsAssignedToUser(credentials.get.providerId).map { ee =>
          println(">>>>>> " + credentials.get.providerId + " // " + ee)
        }

        enrolments.enrolments
          .filter(_.key == "HMRC-PT")
          .flatMap { enrolment =>
            enrolment.identifiers
              .filter(id => id.key == "NINO")
          } match {
          case enrolmentIdentifiers if enrolmentIdentifiers.isEmpty =>
            Future.successful(Forbidden("No HMRC-PT enrolment present"))
          case enrolmentIdentifiers
              if enrolmentIdentifiers.exists(enrolmentIdentifier => enrolmentIdentifier.value == nino) =>
            Future.successful(Ok("Successful"))
          case _ =>
            logger.logEvent(logSuccessfulRedirectToReturnUrl)
            Future.successful(Forbidden("HMRC-PT enrolment present with wrong nino"))
        }

      case _ => Future.successful(InternalServerError("Server error"))
    }
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

  val authStub: Action[AnyContent] = Action { request =>
    Ok
  }

  val taxEnrolmentsStub: Action[AnyContent] = Action { _ =>
    NoContent
  }
}
