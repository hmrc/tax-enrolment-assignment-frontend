package uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms

import play.api.libs.json.{Format, Json}

case class RecogniseUserIds(recogniseUserIds: Boolean)

object RecogniseUserIds {
  implicit val format: Format[RecogniseUserIds] = Json.format[RecogniseUserIds]
}
