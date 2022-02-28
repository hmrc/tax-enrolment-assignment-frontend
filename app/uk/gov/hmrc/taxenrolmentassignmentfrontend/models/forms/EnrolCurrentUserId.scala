package uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms

import play.api.libs.json.{Format, Json}

case class EnrolCurrentUserId(enrolCurrentUserId: Boolean)

object EnrolCurrentUserId {
  implicit val format: Format[EnrolCurrentUserId] =
    Json.format[EnrolCurrentUserId]
}
