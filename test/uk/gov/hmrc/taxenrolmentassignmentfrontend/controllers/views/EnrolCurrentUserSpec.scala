package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.views

import org.jsoup.Jsoup
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Injecting
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.controllers.messages.EnrolCurrentUserMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.EnrolCurrentUserIdForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolCurrentUser

class EnrolCurrentUserSpec
    extends AnyWordSpec
    with Injecting
    with Matchers
    with TestFixture {

  lazy val view: EnrolCurrentUser = inject[EnrolCurrentUser]
  val fixedCurrentUserId = "*********9871"
  val fixedSAUserId = "*********9872"

  val form = EnrolCurrentUserIdForm.enrolCurrentUserIdForm

  object Selectors {
    val title = "title"
    val heading = "h1"
    val radios = ""
  }

  "EnrolCurrentUser" when {
    "the user has another account with self assessment and no form errors" should {
      val documentWithSA =
        Jsoup.parse(
          view(form, fixedCurrentUserId, Some(fixedSAUserId))(
            fakeRequest,
            messages
          ).toString()
        )
      "have the expected title" in {
        documentWithSA
          .select(Selectors.title)
          .text() shouldBe EnrolCurrentUserMessages.title
      }
      "have the expected heading" in {
        documentWithSA
          .select(Selectors.heading)
          .text() shouldBe EnrolCurrentUserMessages.title
      }
    }
  }
}
