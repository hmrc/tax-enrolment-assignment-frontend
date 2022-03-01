package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.controllers.messages

object EnrolCurrentUserMessages {

  val continue = "Continue"
  val title =
    "Which Government Gateway user ID do you want to use to access your personal tax information?"
  val heading =
    "Which Government Gateway user ID do you want to use to access your personal tax information?"
  def radioCurrentUserId(id: String) = s"My current user ID $id"
  val radioOtherUserId = "Another user ID"
  def warning(id: String) =
    s"You have access to Self Assessment under user ID $id. We recommend signing back in with this user ID so that you can access Self Assessment from your personal tax account."
  val errorHeading = "There is a problem"
  val errorMessage = "Confirm which Government Gateway user ID you want to use"
}
