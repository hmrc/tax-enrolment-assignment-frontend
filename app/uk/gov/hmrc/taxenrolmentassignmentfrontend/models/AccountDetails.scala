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

import java.time
import java.time.format.{DateTimeFormatter, FormatStyle}
import java.time.{Instant, ZoneId, ZonedDateTime}

import play.api.libs.json.{Format, Json}

case class MFADetails(factorName: String, factorValue: String) {
  def this(additonalFactors: AdditonalFactors) =
    this(factorName = additonalFactors.factorType match {
      case "totp"  => "Authentication app"
      case "voice" => "Voice call"
      case _       => "Text message"
    }, factorValue = additonalFactors.factorType match {
      case "totp" => additonalFactors.name.getOrElse("")
      case _      => additonalFactors.phoneNumber.getOrElse("")
    })
}

object MFADetails {
  implicit val format: Format[MFADetails] = Json.format[MFADetails]
}

case class AccountDetails(userId: String,
                          email: Option[String],
                          lastLoginDate: String,
                          mfaDetails: List[MFADetails]) {
  def this(usersGroupResponse: UsersGroupResponse) =
    this(
      userId = usersGroupResponse.obfuscatedUserId,
      email = usersGroupResponse.email,
      lastLoginDate =
        AccountDetails.formatDate(usersGroupResponse.lastAccessedTimestamp),
      mfaDetails = usersGroupResponse.additionalFactors.map {
        additionalFactor =>
          new MFADetails(additionalFactor)
      }
    )
}

object AccountDetails {

  def formatDate(date: String): String = {
    val zonedDateTime = ZonedDateTime.parse(date)
    val datetimeFormatter = {
      DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    }
    if (isToday(zonedDateTime)) {
      "Today"
    } else if (isYesterday(zonedDateTime)) {
      "Yesterday"
    } else {
      zonedDateTime.format(datetimeFormatter)
    }
  }

  def isToday(date: ZonedDateTime): Boolean = {
    val todaysDate = ZonedDateTime.now(ZoneId.of("UTC"))
    todaysDate.getYear == date.getYear && todaysDate.getMonth == date.getMonth && todaysDate.getDayOfMonth == date.getDayOfMonth
  }

  def isYesterday(date: ZonedDateTime): Boolean = {
    val yesterdaysDate = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1L)
    yesterdaysDate.getYear == date.getYear && yesterdaysDate.getMonth == date.getMonth && yesterdaysDate.getDayOfMonth == date.getDayOfMonth
  }

  implicit val format: Format[AccountDetails] = Json.format[AccountDetails]
}
