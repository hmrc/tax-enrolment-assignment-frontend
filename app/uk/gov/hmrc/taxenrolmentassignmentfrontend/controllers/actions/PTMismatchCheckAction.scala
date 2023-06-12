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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import cats.data.OptionT
import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.mvc.Results.Redirect
import play.api.mvc.{ActionFunction, Result}
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequestAndSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.ErrorHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{EnrolmentStoreServiceUnavailable, InvalidRedirectUrl}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logInvalidRedirectUrl
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService
import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class PTMismatchCheckActionImpl @Inject() (
  eacdService: EACDService,
  appConfig: AppConfig,
  val logger: EventLoggerService,
  errorHandler: ErrorHandler
)(implicit
  ec: ExecutionContext
) extends PTMismatchCheckAction {

  implicit lazy val baseLogger: Logger = Logger(this.getClass.getName)

  def invokeBlock[A](
    request: RequestWithUserDetailsFromSession[A],
    block: RequestWithUserDetailsFromSession[A] => Future[Result]
  ): Future[Result] =
    if (appConfig.ptNinoMismatchToggle()) {
      implicit val hc: HeaderCarrier = fromRequestAndSession(request, request.session)
      implicit val userDetails: UserDetailsFromSession = request.userDetails
      val ptEnrolment = userDetails.enrolments.getEnrolment(s"$hmrcPTKey")
      ptEnrolment
        .map { enrolment =>
          ptMismatchCheckAndDelete(enrolment, userDetails.nino, userDetails.groupId).foldF {
            Future.successful(
              errorHandler.handleErrors(EnrolmentStoreServiceUnavailable, "[PTMismatchCheckAction][invokeBlock]")(
                request,
                implicitly
              )
            )
          }(result =>
            if (result) {
              request.request.getQueryString("redirectUrl") match {
                case Some(url) =>
                  Future.successful(
                    Redirect(
                      routes.AccountCheckController
                        .accountCheck(
                          RedirectUrl(url)
                        )
                        .url
                    )
                  )
                case None =>
                  logger.logEvent(logInvalidRedirectUrl("Redirect url is missing from the query string"))
                  Future.successful(
                    errorHandler
                      .handleErrors(InvalidRedirectUrl, "[AccountCheckController][accountCheck]")(request, implicitly)
                  )
              }
            } else {
              block(request)
            }
          )
        }
        .getOrElse {
          block(request)
        }
    } else {
      block(request)
    }

  private def ptMismatchCheckAndDelete(enrolment: Enrolment, nino: String, groupId: String)(implicit
    hc: HeaderCarrier
  ): OptionT[Future, Boolean] = {
    val ptNino = enrolment.identifiers.find(_.key == "NINO").map(_.value)
    if (ptNino.getOrElse("") != nino) {
      eacdService.deallocateEnrolment(groupId, s"$hmrcPTKey~NINO~$ptNino").map(_ => true).toOption
    } else {
      OptionT.fromOption(Some(false))
    }
  }

  override protected def executionContext: ExecutionContext = ec
}

@ImplementedBy(classOf[PTMismatchCheckActionImpl])
trait PTMismatchCheckAction extends ActionFunction[RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSession]
