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
import play.api.libs.json.{Format, JsObject, Json}
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec

class AccountDetailsSpec extends BaseSpec {
  private case class DateInfo(month: String, sourceDate: String, expectedDate: String)
  private val mfaDetailsText  = MFADetails("mfaDetails.text", "24321")
  private val mfaDetailsVoice = MFADetails("mfaDetails.voice", "24321")
  private val mfaDetailsTotp  = MFADetails("mfaDetails.totp", "HMRC App")

  private def accountDetails(formattedLastLoginDate: String, mfaDetails: List[MFADetails]): AccountDetails =
    AccountDetails(
      identityProviderType = SCP,
      "credId",
      "6037",
      Some(SensitiveString("email1@test.com")),
      Some(formattedLastLoginDate),
      mfaDetails
    )

  private def accountDetailsForEachMonth(seqDateInfo: Seq[DateInfo], lang: String): Unit =
    seqDateInfo.foreach { test =>
      s"${test.month} should display ${test.expectedDate} for ${test.sourceDate} for language $lang" in {
        val expectedResult = accountDetails(test.expectedDate, List(mfaDetailsText))
        val res            = AccountDetails.userFriendlyAccountDetails(
          AccountDetails(
            identityProviderType = SCP,
            "credId",
            "********6037",
            Some(SensitiveString("email1@test.com")),
            Some(test.sourceDate),
            List(mfaDetailsText)
          )
        )(messagesApi.preferred(List(Lang(lang))))
        res shouldBe expectedResult
      }
    }
  "userFriendlyAccountDetails" must {
    behave like accountDetailsForEachMonth(
      Seq(
        DateInfo("January", "2022-01-27T12:00:27Z", "27 January 2022 at 12:00PM"),
        DateInfo("February", "2022-02-27T12:00:27Z", "27 February 2022 at 12:00PM"),
        DateInfo("March", "2022-03-27T12:00:27Z", "27 March 2022 at 1:00PM"),
        DateInfo("April", "2022-04-27T12:00:27Z", "27 April 2022 at 1:00PM"),
        DateInfo("May", "2022-05-27T12:00:27Z", "27 May 2022 at 1:00PM"),
        DateInfo("June", "2022-06-27T12:00:27Z", "27 June 2022 at 1:00PM"),
        DateInfo("July", "2022-07-27T12:00:27Z", "27 July 2022 at 1:00PM"),
        DateInfo("August", "2022-08-27T12:00:27Z", "27 August 2022 at 1:00PM"),
        DateInfo("September", "2022-09-27T17:00:27Z", "27 September 2022 at 6:00PM"),
        DateInfo("October", "2022-10-27T11:00:27Z", "27 October 2022 at 12:00PM"),
        DateInfo("November", "2022-11-27T09:00:27Z", "27 November 2022 at 9:00AM"),
        DateInfo("December", "2022-12-27T15:00:27Z", "27 December 2022 at 3:00PM")
      ),
      "en"
    )

    behave like accountDetailsForEachMonth(
      Seq(
        DateInfo("Ionawr", "2022-01-27T12:00:27Z", "27 Ionawr 2022 am 12:00PM"),
        DateInfo("Chwefror", "2022-02-27T12:00:27Z", "27 Chwefror 2022 am 12:00PM"),
        DateInfo("Mawrth", "2022-03-27T12:00:27Z", "27 Mawrth 2022 am 1:00PM"),
        DateInfo("Ebrill", "2022-04-27T12:00:27Z", "27 Ebrill 2022 am 1:00PM"),
        DateInfo("Mai", "2022-05-27T12:00:27Z", "27 Mai 2022 am 1:00PM"),
        DateInfo("Mehefin", "2022-06-27T12:00:27Z", "27 Mehefin 2022 am 1:00PM"),
        DateInfo("Gorffennaf", "2022-07-27T12:00:27Z", "27 Gorffennaf 2022 am 1:00PM"),
        DateInfo("Awst", "2022-08-27T12:00:27Z", "27 Awst 2022 am 1:00PM"),
        DateInfo("Medi", "2022-09-27T17:00:27Z", "27 Medi 2022 am 6:00PM"),
        DateInfo("Hydref", "2022-10-27T11:00:27Z", "27 Hydref 2022 am 12:00PM"),
        DateInfo("Tachwedd", "2022-11-27T09:00:27Z", "27 Tachwedd 2022 am 9:00AM"),
        DateInfo("Rhagfyr", "2022-12-27T15:00:27Z", "27 Rhagfyr 2022 am 3:00PM")
      ),
      "cy"
    )

    "have an sms additionalFactor" should {
      "return the expected account details" in {
        val lastAccessedDate =
          "2022-02-27T12:00:27Z"

        val expectedResult = accountDetails("27 February 2022 at 12:00PM", List(mfaDetailsText))

        val res = AccountDetails.userFriendlyAccountDetails(
          AccountDetails(
            identityProviderType = SCP,
            "credId",
            "********6037",
            Some(SensitiveString("email1@test.com")),
            Some(lastAccessedDate),
            List(mfaDetailsText)
          )
        )(messages)

        res shouldBe expectedResult
      }
    }

    "have a voice additionalFactor" should {
      "return the expected account details" in {
        val lastAccessedDate =
          "2022-02-27T12:00:27Z"

        val expectedResult =
          accountDetails("27 February 2022 at 12:00PM", List(mfaDetailsVoice))

        val res = AccountDetails.userFriendlyAccountDetails(
          AccountDetails(
            identityProviderType = SCP,
            "credId",
            "********6037",
            Some(SensitiveString("email1@test.com")),
            Some(lastAccessedDate),
            List(mfaDetailsVoice),
            None
          )
        )(messages)

        res shouldBe expectedResult
      }
    }

    "have a lastAccessedTimestamp over 2 days ago and a totp additionalFactor" should {
      "return the expected account details" in {
        val lastAccessedDate =
          "2022-02-27T12:00:27Z"

        val expectedResult =
          accountDetails("27 February 2022 at 12:00PM", List(mfaDetailsTotp))

        val res = AccountDetails.userFriendlyAccountDetails(
          AccountDetails(
            identityProviderType = SCP,
            "credId",
            "********6037",
            Some(SensitiveString("email1@test.com")),
            Some(lastAccessedDate),
            List(mfaDetailsTotp)
          )
        )(messages)

        res shouldBe expectedResult
      }
    }

    "have a lastAccessedTimestamp over 2 days ago and a more than one factor" should {
      "return the expected account details" in {
        val lastAccessedDate =
          "2022-03-27T12:00:27Z"

        val expectedResult = accountDetails(
          "27 March 2022 at 1:00PM",
          List(mfaDetailsText, mfaDetailsVoice, mfaDetailsTotp)
        )
        val res            = AccountDetails.userFriendlyAccountDetails(
          AccountDetails(
            identityProviderType = SCP,
            "credId",
            "********6037",
            Some(SensitiveString("email1@test.com")),
            Some(lastAccessedDate),
            List(mfaDetailsText, mfaDetailsVoice, mfaDetailsTotp)
          )
        )(messages)

        res shouldBe expectedResult
      }
    }
  }

  "mongoFormats" should {
    "write correctly to json" in {
      val accountDetails =
        AccountDetails(
          identityProviderType = SCP,
          "credid",
          "userId",
          Some(SensitiveString("foo@test.com")),
          Some("lastLoginDate"),
          Seq(mfaDetailsTotp),
          None
        )

      val res = Json.toJson(accountDetails)(AccountDetails.mongoFormats(crypto.crypto))
      res.as[JsObject] - "email" shouldBe Json.obj(
        "identityProviderType" -> "SCP",
        "credId"               -> "credid",
        "userId"               -> "userId",
        "lastLoginDate"        -> "lastLoginDate",
        "mfaDetails"           -> Json.arr(
          Json.obj("factorNameKey" -> "mfaDetails.totp", "factorValue" -> "HMRC App")
        )
      )

      crypto.crypto.decrypt(Crypted(res.as[JsObject].value("email").as[String])).value shouldBe """"foo@test.com""""
    }
    "read from json" in {
      implicit val ssf: Format[SensitiveString] =
        JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)(implicitly, crypto.crypto)
      val json                                  = Json.obj(
        "identityProviderType" -> "SCP",
        "credId"               -> "credid",
        "userId"               -> "userId",
        "lastLoginDate"        -> "lastLoginDate",
        "email"                -> SensitiveString("foooo@test.com"),
        "mfaDetails"           -> Json.arr(
          Json.obj("factorNameKey" -> "mfaDetails.totp", "factorValue" -> "HMRC App")
        )
      )

      val accountDetails =
        AccountDetails(
          identityProviderType = SCP,
          "credid",
          "userId",
          Some(SensitiveString("foooo@test.com")),
          Some("lastLoginDate"),
          Seq(mfaDetailsTotp),
          None
        )

      Json.fromJson(json)(AccountDetails.mongoFormats(crypto.crypto)).get shouldBe accountDetails
      accountDetails.emailDecrypted                                       shouldBe Some("foooo@test.com")
      accountDetails.emailObfuscated                                      shouldBe Some("f***o@test.com")
    }
  }
}
