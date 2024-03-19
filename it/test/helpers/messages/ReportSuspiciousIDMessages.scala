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

package helpers.messages

object ReportSuspiciousIDMessages {

  val title = "You need to contact us"
  val heading = "You need to contact us"
  val paragraph1: String = "This is so we can investigate this user ID. " +
    "We recommend you take a note of the user ID and email address below as this screen will time out after 15 minutes."
  val button = "Continue"
  val telephone: Seq[String] = Seq("Telephone:", "0300 200 3600")
  val outsideUK: Seq[String] = Seq("Outside UK:", "+44 161 930 8445")

  val titleWelsh = "Diogelu’ch treth"

  val informationBlock: Seq[String] = Seq(
    "Information about opening hours, Relay UK and call charges",
    "Opening hours are Monday to Friday: 8am to 6pm (closed weekends and bank holidays).",
    "You can also use Relay UK (opens in new tab) if you cannot hear or speak on the phone: dial 18001 then 0300 200 3600.",
    "Calls are charged at standard local rates but may be free to call depending on your phone tariff."
  )

  val detailBlockLink = "Relay UK (opens in new tab)"

  val saPText =
    "You can continue to your personal tax account with the user ID you are currently signed in with."

  val relayUkLinkUrl = "https://www.relayuk.bt.com/"

  val action =
    "/protect-tax-info/enrol-pt/contact-hmrc-sa"

  val referenceNumberAndHelpdeskTiming =
    "Call the Online Service Helpdesk and quote reference PTA951. The helpdesk is available Monday to Friday 8am to 6pm."

}
