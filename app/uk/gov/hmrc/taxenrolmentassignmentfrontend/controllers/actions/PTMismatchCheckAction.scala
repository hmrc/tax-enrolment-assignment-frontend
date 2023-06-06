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

import com.google.inject.ImplementedBy
import play.api.Logging
import play.api.mvc.Results.Redirect
import play.api.mvc.{ActionFunction, Result}
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequestAndSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PTMismatchCheckActionImpl @Inject() (
  eacdService: EACDService,
  appConfig: AppConfig
)(implicit
  ec: ExecutionContext
) extends PTMismatchCheckAction with Logging {

  def invokeBlock[A](
    request: RequestWithUserDetailsFromSession[A],
    block: RequestWithUserDetailsFromSession[A] => Future[Result]
  ): Future[Result] =
    if (appConfig.ptNinoMismatchToggle()) {
      println("1" * 100)

      implicit val hc: HeaderCarrier = fromRequestAndSession(request, request.session)
      implicit val userDetails: UserDetailsFromSession = request.userDetails
      val ptEnrolment = userDetails.enrolments.getEnrolment(s"$hmrcPTKey")
      println("2" * 100)
      ptEnrolment
        .map { enrolment =>
          ptMismatchCheck(enrolment, userDetails.nino, userDetails.groupId).map {
            case true =>
              request.request.getQueryString("redirectUrl") match {
                case Some(url) =>
                  println("3" * 100)

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
                  println("4" * 100)
                  val ex = new RuntimeException(s"Redirect url is missing from the query string")
                  logger.error(ex.getMessage, ex)
                  block(request)
              }
            case _ =>
              println("5" * 100)
              block(request)
          }.flatten
        }
        .getOrElse {
          println("6" * 100)
          block(request)
        }
    } else {
      println("7" * 100)
      block(request)
    }

  private def ptMismatchCheck(enrolment: Enrolment, nino: String, groupId: String)(implicit
    hc: HeaderCarrier
  ): Future[Boolean] = {
    val ptNino = enrolment.identifiers.find(_.key == "NINO").map(_.value)
    if (ptNino.getOrElse("") != nino) {
      println("a" * 100)
      eacdService.deallocateEnrolment(groupId, s"$hmrcPTKey~NINO~$ptNino").isRight
    } else {
      println("b" * 100)
      Future.successful(false)
    }
  }

  override protected def executionContext: ExecutionContext = ec
}

@ImplementedBy(classOf[PTMismatchCheckActionImpl])
trait PTMismatchCheckAction extends ActionFunction[RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSession]
