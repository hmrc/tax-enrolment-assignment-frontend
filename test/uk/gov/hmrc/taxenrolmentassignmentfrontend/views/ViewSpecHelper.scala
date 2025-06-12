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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.views

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout}
import play.twirl.api.Html
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

import scala.jdk.CollectionConverters._

trait ViewSpecHelper extends BaseSpec {
  implicit lazy val testMessages: Messages =
    messagesApi.preferred(FakeRequest())

  def doc(result: Html): Document = Jsoup.parse(contentAsString(result))

  lazy val errorView: ErrorTemplate = app.injector.instanceOf[ErrorTemplate]

  def validateTimeoutDialog(doc: Document): Unit = {
    val timeoutDialog = doc
      .select("meta")
      .asScala
      .toList
      .collectFirst {
        case element if element.attr("name") == "hmrc-timeout-dialog" => element
      }
    "contain the timeout dialog" that {
      "has the expected keep alive and signout urls" in {
        timeoutDialog.isDefined                       shouldBe true
        timeoutDialog.get.attr("data-keep-alive-url") shouldBe "/protect-tax-info/keep-alive"
        timeoutDialog.get.attr("data-sign-out-url")   shouldBe "/protect-tax-info/timeout"
      }
    }
  }

  def validateNoTimeoutDialog(doc: Document): Unit = {
    val timeoutDialog = doc
      .select("meta")
      .asScala
      .toList
      .collectFirst {
        case element if element.attr("name") == "hmrc-timeout-dialog" => element
      }
    "not contain the timeout dialog" in {
      timeoutDialog.isDefined shouldBe false
    }
  }

  def validateTechnicalHelpLinkPresent(doc: Document): Unit = {
    val technicalHelpLink = doc
      .getElementsByClass("govuk-link hmrc-report-technical-issue ")
    "contain a technical help link" that {
      "has the expected text" in {
        technicalHelpLink
          .text() shouldBe "Is this page not working properly? (opens in new tab)"
      }
      "has expected href" in {
        assert(
          technicalHelpLink
            .attr("href")
            .contains("/contact/report-technical-problem")
        )
      }
    }
  }

  def validateAccessibilityStatementLinkPresent(doc: Document): Unit = {
    val accessibilityStatementElement = doc
      .getElementsByAttributeValueContaining("href", "/accessibility-statement/personal-tax-account-user-id-checks")
      .get(0)

    "accessibility statement exists, text and link are correct" in {
      accessibilityStatementElement.text() shouldBe "Accessibility statement"
    }
  }

}
