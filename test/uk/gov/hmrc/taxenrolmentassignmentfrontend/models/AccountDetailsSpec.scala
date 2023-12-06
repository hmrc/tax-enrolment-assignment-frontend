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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models

import play.api.i18n.Lang
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec

class AccountDetailsSpec extends BaseSpec {

  val additionalFactorText = AdditonalFactors("sms", Some("07783924321"))
  val additionalFactorVoice = AdditonalFactors("voice", Some("07783924321"))
  val additionalFactorTotp = AdditonalFactors("totp", name = Some("HMRC App"))

  val mfaDetailsText = MFADetails("mfaDetails.text", "24321")
  val mfaDetailsVoice = MFADetails("mfaDetails.voice", "24321")
  val mfaDetailsTotp = MFADetails("mfaDetails.totp", "HMRC App")

  def usersGroupResponse(
    lastAccessedTime: String,
    additionalFactors: List[AdditonalFactors]
  ): UsersGroupResponse =
    UsersGroupResponse(
      obfuscatedUserId = "********6037",
      email = Some("email1@test.com"),
      lastAccessedTimestamp = Some(lastAccessedTime),
      additionalFactors = Some(additionalFactors)
    )

  def accountDetails(formattedLastLoginDate: String, mfaDetails: List[MFADetails]): AccountDetails =
    AccountDetails(
      "credId",
      "6037",
      Some(SensitiveString("email1@test.com")),
      formattedLastLoginDate,
      mfaDetails
    )

  "userFriendlyAccountDetails" when {
    "userFriendlyAccountDetails is called with Welsh Messages" that {
      Map(
        "Ionawr"     -> "2022-01-27T12:00:27Z",
        "Chwefror"   -> "2022-02-27T12:00:27Z",
        "Mawrth"     -> "2022-03-27T12:00:27Z",
        "Ebrill"     -> "2022-04-27T12:00:27Z",
        "Mai"        -> "2022-05-27T12:00:27Z",
        "Mehefin"    -> "2022-06-27T12:00:27Z",
        "Gorffennaf" -> "2022-07-27T12:00:27Z",
        "Awst"       -> "2022-08-27T12:00:27Z",
        "Medi"       -> "2022-09-27T12:00:27Z",
        "Hydref"     -> "2022-10-27T12:00:27Z",
        "Tachwedd"   -> "2022-11-27T12:00:27Z",
        "Rhagfyr"    -> "2022-12-27T12:00:27Z"
      ).foreach { test =>
        s"${test._1} should display correctly for ${test._2}" in {
          val expectedResult = accountDetails(s"27 ${test._1} 2022 am 12:00 PM", List(mfaDetailsText))

          val res = AccountDetails.userFriendlyAccountDetails(
            AccountDetails(
              "credId",
              "********6037",
              Some(SensitiveString("email1@test.com")),
              test._2,
              List(mfaDetailsText)
            )
          )(messagesApi.preferred(List(Lang("cy"))))

          res shouldBe expectedResult
        }
      }
    }
    "userFriendlyAccountDetails is called with English Messages" that {
      Map(
        "January"   -> "2022-01-27T12:00:27Z",
        "February"  -> "2022-02-27T12:00:27Z",
        "March"     -> "2022-03-27T12:00:27Z",
        "April"     -> "2022-04-27T12:00:27Z",
        "May"       -> "2022-05-27T12:00:27Z",
        "June"      -> "2022-06-27T12:00:27Z",
        "July"      -> "2022-07-27T12:00:27Z",
        "August"    -> "2022-08-27T12:00:27Z",
        "September" -> "2022-09-27T12:00:27Z",
        "October"   -> "2022-10-27T12:00:27Z",
        "November"  -> "2022-11-27T12:00:27Z",
        "December"  -> "2022-12-27T12:00:27Z"
      ).foreach { test =>
        s"${test._1} should display correctly for ${test._2}" in {
          val expectedResult = accountDetails(s"27 ${test._1} 2022 at 12:00 PM", List(mfaDetailsText))

          val res = AccountDetails.userFriendlyAccountDetails(
            AccountDetails(
              "credId",
              "********6037",
              Some(SensitiveString("email1@test.com")),
              test._2,
              List(mfaDetailsText)
            )
          )(messagesApi.preferred(List(Lang("en"))))

          res shouldBe expectedResult
        }
      }
      "has a sms additionalFactor" should {
        "return the expected account details" in {
          val lastAccessedDate =
            "2022-02-27T12:00:27Z"

          val expectedResult = accountDetails("27 February 2022 at 12:00 PM", List(mfaDetailsText))

          val res = AccountDetails.userFriendlyAccountDetails(
            AccountDetails(
              "credId",
              "********6037",
              Some(SensitiveString("email1@test.com")),
              lastAccessedDate,
              List(mfaDetailsText)
            )
          )(messages)

          res shouldBe expectedResult
        }
      }

      "has a voice additionalFactor" should {
        "return the expected account details" in {
          val lastAccessedDate =
            "2022-02-27T12:00:27Z"

          val expectedResult =
            accountDetails("27 February 2022 at 12:00 PM", List(mfaDetailsVoice))

          val res = AccountDetails.userFriendlyAccountDetails(
            AccountDetails(
              "credId",
              "********6037",
              Some(SensitiveString("email1@test.com")),
              lastAccessedDate,
              List(mfaDetailsVoice),
              None
            )
          )(messages)

          res shouldBe expectedResult
        }
      }

      "has a lastAccessedTimestamp over 2 days ago and a totp additionalFactor" should {
        "return the expected account details" in {
          val lastAccessedDate =
            "2022-02-27T12:00:27Z"

          val expectedResult =
            accountDetails("27 February 2022 at 12:00 PM", List(mfaDetailsTotp))

          val res = AccountDetails.userFriendlyAccountDetails(
            AccountDetails(
              "credId",
              "********6037",
              Some(SensitiveString("email1@test.com")),
              lastAccessedDate,
              List(mfaDetailsTotp)
            )
          )(messages)

          res shouldBe expectedResult
        }
      }

      "has a lastAccessedTimestamp over 2 days ago and a more than one factor" should {
        "return the expected account details" in {
          val lastAccessedDate =
            "2022-03-27T12:00:27Z"

          val expectedResult = accountDetails(
            "27 March 2022 at 12:00 PM",
            List(mfaDetailsText, mfaDetailsVoice, mfaDetailsTotp)
          )

          val res = AccountDetails.userFriendlyAccountDetails(
            AccountDetails(
              "credId",
              "********6037",
              Some(SensitiveString("email1@test.com")),
              lastAccessedDate,
              List(mfaDetailsText, mfaDetailsVoice, mfaDetailsTotp)
            )
          )(messages)

          res shouldBe expectedResult
        }
      }
    }
  }

  "mongoFormats" should {
    "write correctly to json" in {
      val accountDetails =
        AccountDetails("credid", "userId", Some(SensitiveString("foo")), "lastLoginDate", Seq(mfaDetailsTotp), None)

      val res = Json.toJson(accountDetails)(AccountDetails.mongoFormats(crypto.crypto))
      res.as[JsObject] - "email" shouldBe Json.obj(
        "credId"        -> "credid",
        "userId"        -> "userId",
        "lastLoginDate" -> "lastLoginDate",
        "mfaDetails" -> Json.arr(
          Json.obj("factorNameKey" -> "mfaDetails.totp", "factorValue" -> "HMRC App")
        )
      )

      crypto.crypto.decrypt(Crypted(res.as[JsObject].value("email").as[String])).value shouldBe """"foo""""
    }
    "read from json" in {
      implicit val ssf = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)(implicitly, crypto.crypto)
      val json = Json.obj(
        "credId"        -> "credid",
        "userId"        -> "userId",
        "lastLoginDate" -> "lastLoginDate",
        "email"         -> SensitiveString("foo"),
        "mfaDetails" -> Json.arr(
          Json.obj("factorNameKey" -> "mfaDetails.totp", "factorValue" -> "HMRC App")
        )
      )

      val accountDetails =
        AccountDetails("credid", "userId", Some(SensitiveString("foo")), "lastLoginDate", Seq(mfaDetailsTotp), None)
      Json.fromJson(json)(AccountDetails.mongoFormats(crypto.crypto)).get shouldBe accountDetails
      accountDetails.emailDecrypted shouldBe Some("foo")
    }
  }
}
