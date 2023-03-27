package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import com.google.inject.ImplementedBy
import play.api.mvc.{ActionFunction, Result}
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequestAndSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PTMismatchCheckActionImpl @Inject() (
                                      eacdService: EACDService
                                   )(implicit
                                     ec: ExecutionContext
                                   ) extends PTMismatchCheckAction {

  def invokeBlock[A](
                      request: RequestWithUserDetailsFromSession[A],
                      block: RequestWithUserDetailsFromSession[A] => Future[Result]
                    ): Future[Result] = {
    implicit val hc: HeaderCarrier                        = fromRequestAndSession(request, request.session)
    implicit val userDetails: UserDetailsFromSession = request.userDetails
    val ptEnrolment = userDetails.enrolments.getEnrolment(s"$hmrcPTKey")

    ptEnrolment.map(enrolment => {
      ptMismatchCheck(enrolment, userDetails.nino, userDetails.groupId).map {
        case true =>
          eacdService.deallocateEnrolment(userDetails.groupId, s"$hmrcPTKey")
          block(request)
        case _ => block(request)
      }.flatten
    }).getOrElse(block(request))
  }

    private def ptMismatchCheck(enrolment: Enrolment, nino: String, groupId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
      val ptNino = enrolment.identifiers.find(_.key == "NINO").map(_.value)

      if (ptNino.getOrElse("") != nino) {
        eacdService.deallocateEnrolment(groupId, s"HMRC-PT~NINO~$ptNino")
      } else {
        Future.successful(false)
      }
    }

  override protected def executionContext: ExecutionContext = ec
}

@ImplementedBy(classOf[PTMismatchCheckActionImpl])
trait PTMismatchCheckAction extends ActionFunction[RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSession]
