package uk.gov.hmrc.taxenrolmentassignmentfrontend.models.audit

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.model.DataEvent

class ReportUnrecognisedAccountAudit(reportType: String,
                                     auditPath: String,
                                     data: Map[String, String])(implicit hc: HeaderCarrier)
  extends DataEvent(
    auditSource = "tax-enrolment-assignment-frontend",
    auditType = "ReportUnrecognisedAccount",
    detail =  data,
    tags = hc.toAuditTags(
      transactionName = s"reporting-unrecognised-$reportType-account",
      path = auditPath
    )
  )

object ReportUnrecognisedAccountAudit {
  implicit val format: OFormat[ReportUnrecognisedAccountAudit] = Json.format[ReportUnrecognisedAccountAudit]
}
