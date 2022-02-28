package uk.gov.hmrc.taxenrolmentassignmentfrontend.forms

import play.api.data.Form
import play.api.data.Forms.{default, mapping, nonEmptyText, text}
import play.api.data.validation.Constraints._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.RecogniseUserIds

object RecogniseUserIdsForm {

  val recogniseUserIdsForm: Form[RecogniseUserIds] = Form(
    mapping(
      "recogniseUserIds" -> default[String](text, "")
        .verifying(
          nonEmpty(errorMessage = "recogniseUserIds.error.required"),
          pattern("Yes|No".r, "recogniseUserIds.error.required")
        )
        .transform[Boolean](_ == "Yes", x => if (x) "Yes" else "No")
    )(RecogniseUserIds.apply)(RecogniseUserIds.unapply)
  )
}
