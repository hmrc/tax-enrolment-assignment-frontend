package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import play.api.libs.json.Json
import play.api.mvc.AnyContent
import uk.gov.hmrc.audit.HandlerResult
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.DefaultAuditChannel
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.MFADetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.audit.{ReportAuditDetailsData, ReportUnrecognisedAccountAudit}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditService @Inject()(auditChannel: DefaultAuditChannel) {

  def reportUnrecognisedAccountAudit(path: String,
                                     reportedEnrolment: String,
                                     reportDetailsData: ReportAuditDetailsData
                                    )(implicit hc: HeaderCarrier,
                                      ec: ExecutionContext,
                                      req: RequestWithUserDetailsFromSession[AnyContent]): Future[HandlerResult] = {

    val reportDetails = createReportUnrecognisedAccountDetails(reportDetailsData)
    val reportEvent = new ReportUnrecognisedAccountAudit(reportedEnrolment, path, reportDetails)
    auditChannel.send(path, Json.toJson(reportEvent))
  }

  def createReportUnrecognisedAccountDetails(reportDetails: ReportAuditDetailsData)
                                            (implicit hc: HeaderCarrier,
                                             req: RequestWithUserDetailsFromSession[AnyContent]
                                            ): Map[String, String] = {

   val coreDetails: Map[String, String] =  Map(
    "currentAccountUserId" -> reportDetails.currentAccount.userId,
    "currentAccountType" -> reportDetails.currentAccountType,
    "currentAccountAffinityGroup" -> req.userDetails.affinityGroup.toString,
    "currentAccountNINO" -> req.userDetails.nino,
    "reportedAccountUserId" -> reportDetails.reportedAccount.userId,
    "reportedAccountLastSignedIn" -> reportDetails.reportedAccount.lastLoginDate,
    "reportedAccountMFADetails" -> createMFADetails(reportDetails.reportedAccount.mfaDetails)
    )

    def optSaUserID(saDetail: String)  = ("saAccountUserId" -> saDetail)
    def optReportedEmail(repEmail: String) = ("reportedAccountEmail" -> repEmail)

    coreDetails ++
     reportDetails.saAccount.map(acc => optSaUserID(acc.userId)) ++
      reportDetails.reportedAccount.email.map(em => optReportedEmail(em))
  }


  def createMFADetails(factorList: Seq[MFADetails]): String = {
   factorList.map(detail => (
      "factorType" -> detail.factorNameKey,
      "factorValue" -> detail.factorValue
      )
    ).toString()
  }
}
