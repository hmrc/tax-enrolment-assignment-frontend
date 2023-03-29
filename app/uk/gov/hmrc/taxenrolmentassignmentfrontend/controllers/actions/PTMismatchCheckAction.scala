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
import play.api.mvc.{ActionFunction, Result}
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequestAndSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.admin.PtNinoMismatchCheckerToggle
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{EACDService, FeatureFlagService}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PTMismatchCheckActionImpl @Inject() (
                                      eacdService: EACDService,
                                      featureFlagService: FeatureFlagService
                                   )(implicit
                                     ec: ExecutionContext
                                   ) extends PTMismatchCheckAction {

  def invokeBlock[A](
                      request: RequestWithUserDetailsFromSession[A],
                      block: RequestWithUserDetailsFromSession[A] => Future[Result]
                    ): Future[Result] = {
    featureFlagService.get(PtNinoMismatchCheckerToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        implicit val hc: HeaderCarrier = fromRequestAndSession(request, request.session)
        implicit val userDetails: UserDetailsFromSession = request.userDetails
        val ptEnrolment = userDetails.enrolments.getEnrolment(s"$hmrcPTKey")

        ptEnrolment.map(enrolment => {
          ptMismatchCheck(enrolment, userDetails.nino, userDetails.groupId).map {
            case true =>
              block(request)
              /// TODO - Does this need to go back through Auth by way of `Future.successful(Redirect(routes.AccountCheckController.().url))`
            case _ => block(request)
          }.flatten
        }).getOrElse(block(request))
      } else {
        block(request)
      }
    }
  }

  private def ptMismatchCheck(enrolment: Enrolment, nino: String, groupId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val ptNino = enrolment.identifiers.find(_.key == "NINO").map(_.value)

    if (ptNino.getOrElse("") != nino) {
      eacdService.deallocateEnrolment(groupId, s"$hmrcPTKey~NINO~$ptNino").isRight
    } else {
      Future.successful(false)
    }
  }

  override protected def executionContext: ExecutionContext = ec
}

@ImplementedBy(classOf[PTMismatchCheckActionImpl])
trait PTMismatchCheckAction extends ActionFunction[RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSession]
