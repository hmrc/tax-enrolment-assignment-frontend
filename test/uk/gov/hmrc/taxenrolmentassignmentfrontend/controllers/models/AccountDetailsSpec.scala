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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.models
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  AccountDetails,
  AdditonalFactors,
  MFADetails,
  UsersGroupResponse
}

class AccountDetailsSpec extends TestFixture {

  val additionalFactorText = AdditonalFactors("sms", Some("07783924321"))
  val additionalFactorVoice = AdditonalFactors("voice", Some("07783924321"))
  val additionalFactorTotp = AdditonalFactors("totp", name = Some("HMRC App"))

  val mfaDetailsText = MFADetails("mfaDetails.text", "24321")
  val mfaDetailsVoice = MFADetails("mfaDetails.voice", "24321")
  val mfaDetailsTotp = MFADetails("mfaDetails.totp", "HMRC App")

  def usersGroupResponse(
    lastAccessedTime: String,
    additionalFactors: List[AdditonalFactors]
  ): UsersGroupResponse = {
    UsersGroupResponse(
      obfuscatedUserId = "********6037",
      email = Some("email1@test.com"),
      lastAccessedTimestamp = lastAccessedTime,
      additionalFactors = Some(additionalFactors)
    )
  }

  def accountDetails(formattedLastLoginDate: String,
                     mfaDetails: List[MFADetails]): AccountDetails = {
    AccountDetails(
      "6037",
      Some("email1@test.com"),
      formattedLastLoginDate,
      mfaDetails
    )
  }

  "AccountDetails" when {
    "presented with a usersGroupResponse" that {
      "has a lastAccessedTimestamp of today, a sms additionalFactor" should {
        "return the expected account details" in {
          val lastAccessedDate =
            ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
          val ugsResp =
            usersGroupResponse(lastAccessedDate, List(additionalFactorText))

          val expectedResult = accountDetails("Today", List(mfaDetailsText))

          val res = new AccountDetails(ugsResp)

          res shouldBe expectedResult
        }
      }

      "has a lastAccessedTimestamp of yesterday, a voice additionalFactor" should {
        "return the expected account details" in {
          val lastAccessedDate =
            ZonedDateTime
              .now()
              .minusDays(1L)
              .format(DateTimeFormatter.ISO_INSTANT)
          val ugsResp =
            usersGroupResponse(lastAccessedDate, List(additionalFactorVoice))

          val expectedResult =
            accountDetails("Yesterday", List(mfaDetailsVoice))

          val res = new AccountDetails(ugsResp)

          res shouldBe expectedResult
        }
      }

      "has a lastAccessedTimestamp over 2 days ago and a totp additionalFactor" should {
        "return the expected account details" in {
          val lastAccessedDate =
            "2022-02-27T12:00:27Z"
          println(lastAccessedDate)
          val ugsResp =
            usersGroupResponse(lastAccessedDate, List(additionalFactorTotp))

          val expectedResult =
            accountDetails("27 February 2022 at 12:00 PM", List(mfaDetailsTotp))

          val res = new AccountDetails(ugsResp)

          res shouldBe expectedResult
        }
      }

      "has a lastAccessedTimestamp over 2 days ago and a more than one factor" should {
        "return the expected account details" in {
          val lastAccessedDate =
            "2022-02-27T12:00:27Z"
          println(lastAccessedDate)
          val ugsResp =
            usersGroupResponse(
              lastAccessedDate,
              List(
                additionalFactorText,
                additionalFactorVoice,
                additionalFactorTotp
              )
            )

          val expectedResult = accountDetails(
            "27 February 2022 at 12:00 PM",
            List(mfaDetailsText, mfaDetailsVoice, mfaDetailsTotp)
          )

          val res = new AccountDetails(ugsResp)

          res shouldBe expectedResult
        }
      }
    }
  }
}
