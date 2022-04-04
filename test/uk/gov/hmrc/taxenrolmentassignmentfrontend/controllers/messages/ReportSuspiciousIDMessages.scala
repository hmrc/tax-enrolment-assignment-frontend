/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages

object ReportSuspiciousIDMessages {
  val title = "Report a suspicious user ID"
  val heading = "Report a suspicious user ID"
  val paragraph1: String = "You will need to phone us so that we can give you access to your personal tax account. Please"
  val paragraph2: String = "or make a note of it as this screen may time out after 15 minutes."
  val linkParagraphText: String = "download a copy of this information to your device"
  val button = "Continue"
  val saPText = "You can continue to your personal tax account with your current user ID."

  val telephone = Seq("Telephone:","0300 200 3600")
  val outsideUK = Seq("Outside UK:","+44 161 930 8445")

  val informationBlock = Seq(
    "Information about opening hours, call charges and more",
    "Opening hours are Monday to Friday: 8am to 6pm (closed weekends and bank holidays).",
    "You can also use",
    "if you cannot hear or speak on the phone: dial 18001 then 0300 200 3600.",
    "Calls are charged at standard local rates but may be free to call depending on your phone tariff."
  )

  val detailBlockLink = "Relay UK"


}