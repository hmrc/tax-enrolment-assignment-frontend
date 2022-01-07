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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth

import javax.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.auth.core.retrieve.~
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import play.api.mvc.Results._

import scala.concurrent.{ExecutionContext, Future}

case class UserDetailsFromSession(credId: String,
                                  nino: String,
                                  hasPTEnrolment: Boolean,
                                  hasSAEnrolment: Boolean)
case class RequestWithUserDetails[A](request: Request[A],
                                     userDetails: UserDetailsFromSession)
    extends WrappedRequest[A](request)
trait AuthIdentifierAction
    extends ActionBuilder[RequestWithUserDetails, AnyContent]
    with ActionFunction[Request, RequestWithUserDetails]

@Singleton
class AuthAction @Inject()(
  override val authConnector: AuthConnector,
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends AuthorisedFunctions
    with AuthIdentifierAction
    with Logging {

  val origin: String = "tax-enrolment-assignment-frontend"

  override def invokeBlock[A](
    request: Request[A],
    block: RequestWithUserDetails[A] => Future[Result]
  ): Future[Result] = {
    implicit val hc: HeaderCarrier = {
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    }
    authorised(AuthProviders(GovernmentGateway) and ConfidenceLevel.L200)
      .retrieve(nino and credentials and allEnrolments) {
        case Some(nino) ~ Some(credentials) ~ enrolments =>
          val hasSAEnrolment = enrolments.getEnrolment("IR-SA").isDefined
          val hasPTEnrolment = enrolments.getEnrolment("HMRC-PT").isDefined

          val userDetails = UserDetailsFromSession(
            credentials.providerId,
            nino,
            hasPTEnrolment,
            hasSAEnrolment
          )

          block(RequestWithUserDetails(request, userDetails))

        case _ =>
          logger.warn(s"[AuthAction][invokeBlock] session missing credential or NINO field for uri: ${request.uri}")
          Future.successful(Unauthorized)
      } recover {
      case er: NoActiveSession =>
        logger.warn(s"[AuthAction][invokeBlock] no active session for uri: ${request.uri} with message: ${er.getMessage}", er)
        Unauthorized("NoActiveSession")
      case er: AuthorisationException =>
        logger.warn(s"[AuthAction][invokeBlock] Auth exception: ${er.getMessage} for  uri ${request.uri}")
        Unauthorized(er.getMessage)
    }
  }
}
