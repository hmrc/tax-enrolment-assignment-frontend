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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models

import play.api.i18n.Messages
import play.api.libs.json.{Format, Json}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

case class MFADetails(factorNameKey: String, factorValue: String) {
  def this(additonalFactors: AdditonalFactors) =
    this(factorNameKey = additonalFactors.factorType match {
      case "totp"  => "mfaDetails.totp"
      case "voice" => "mfaDetails.voice"
      case _       => "mfaDetails.text"
    }, factorValue = additonalFactors.factorType match {
      case "totp" => additonalFactors.name.getOrElse("")
      case _      => additonalFactors.trimmedPhoneNumber
    })
}

object MFADetails {
  implicit val format: Format[MFADetails] = Json.format[MFADetails]
}

case class AccountDetails(credId: String,
                          userId: String,
                          email: Option[String],
                          lastLoginDate: String,
                          mfaDetails: Seq[MFADetails],
                          hasSA: Option[Boolean] = None) {

}


object AccountDetails {

  def additionalFactorsToMFADetails(additionalFactors: Option[List[AdditonalFactors]]): Seq[MFADetails] = {
    additionalFactors.fold[Seq[MFADetails]](Seq.empty[MFADetails]) { additionalFactors =>
      additionalFactors.map { additionalFactor =>
        new MFADetails(additionalFactor)
      }
    }
  }

  def userFriendlyAccountDetails(accountDetails: AccountDetails)(implicit messages: Messages): AccountDetails = {
    accountDetails.copy(
      credId = accountDetails.credId,
      userId = AccountDetails.trimmedUserId(accountDetails.userId),
      email = accountDetails.email,
      lastLoginDate = AccountDetails.formatDate(accountDetails.lastLoginDate),
    )
  }

  def trimmedUserId(obfuscatedId: String): String =
    obfuscatedId.replaceAll("[*]", "")

  def formatDate(date: String)(implicit messages: Messages): String = {
    val zonedDateTime = ZonedDateTime.parse(date)
    val timeFormatter = {
      DateTimeFormatter.ofPattern("h:mm a")
    }
    s"${zonedDateTime.getDayOfMonth} ${messages(s"common.month${zonedDateTime.getMonth.getValue}")} ${zonedDateTime.getYear} ${messages("common.dateToTime")} ${zonedDateTime.format(timeFormatter)}"
  }

  implicit val format: Format[AccountDetails] = Json.format[AccountDetails]
}
