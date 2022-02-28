package uk.gov.hmrc.taxenrolmentassignmentfrontend.forms

import play.api.data.Form
import play.api.data.Forms.{default, mapping, nonEmptyText, text}
import play.api.data.validation.Constraints._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.EnrolCurrentUserId

object EnrolCurrentUserIdForm {

  val enrolCurrentUserIdForm: Form[EnrolCurrentUserId] = Form(
    mapping(
      "enrolCurrentUserId" -> default[String](text, "")
        .verifying(
          nonEmpty(errorMessage = "enrolCurrentUserId.error.required"),
          pattern("Yes|No".r, "enrolCurrentUserId.error.required")
        )
        .transform[Boolean](_ == "Yes", x => if (x) "Yes" else "No")
    )(EnrolCurrentUserId.apply)(EnrolCurrentUserId.unapply)
  )
}
