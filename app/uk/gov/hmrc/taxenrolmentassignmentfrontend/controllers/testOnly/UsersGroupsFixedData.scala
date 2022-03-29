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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly

import java.time.format.DateTimeFormatter

import play.api.libs.json.{JsArray, JsString, JsValue, Json}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  AdditonalFactors,
  UsersGroupResponse
}

object UsersGroupsFixedData {
  val credId1 = "4684455594391511"
  val userDetails1 =
    UsersGroupResponse(
      "********3469",
      Some("email1@test.com"),
      "2022-01-16T14:40:25Z",
      additionalFactors =
        Some(List(AdditonalFactors("sms", Some("07783924321"))))
    )

  val credId2 = "6508728649413980"
  val userDetails2 =
    UsersGroupResponse(
      "********3980",
      Some("email2@test.com"),
      "2022-01-15T14:40:25Z",
      additionalFactors =
        Some(List(AdditonalFactors("sms", Some("07783924322"))))
    )

  val credId3 = "2338687273700685"
  val userDetails3 =
    UsersGroupResponse(
      "********4229",
      Some("email3@test.com"),
      "2022-01-15T17:40:25Z",
      additionalFactors =
        Some(List(AdditonalFactors("voice", Some("07783924312"))))
    )

  val credId4 = "5052110129550895"
  val userDetails4 =
    UsersGroupResponse(
      "********5216",
      Some("email4@test.com"),
      "2022-01-05T14:40:25Z",
      additionalFactors =
        Some(List(AdditonalFactors("totp", name = Some("HMRC APP"))))
    )

  val credId5 = "6408620249920679"
  val userDetails5 =
    UsersGroupResponse(
      "********0297",
      Some("email5@test.com"),
      "2022-02-16T14:40:25Z",
      additionalFactors =
        Some(List(AdditonalFactors("sms", Some("07783924122"))))
    )

  val credId6 = "1447340264123859"
  val userDetails6 =
    UsersGroupResponse(
      "********6461",
      Some("email6@test.com"),
      "2021-01-16T14:40:25Z",
      additionalFactors =
        Some(List(AdditonalFactors("sms", Some("07783824322"))))
    )

  val credId7 = "8970021278265987"
  val userDetails7 =
    UsersGroupResponse(
      "********1655",
      Some("email7@test.com"),
      "2022-09-16T14:40:25Z",
      additionalFactors =
        Some(List(AdditonalFactors("voice", Some("07783424322"))))
    )

  val usersGroupSearchCreds = Map(
    credId1 -> userDetails1,
    credId2 -> userDetails2,
    credId3 -> userDetails3,
    credId4 -> userDetails4,
    credId5 -> userDetails5,
    credId6 -> userDetails6,
    credId7 -> userDetails7
  )

  def toJson(usersGroupResponse: UsersGroupResponse): JsValue = {
    val datetimeFormatter = DateTimeFormatter.ISO_INSTANT

    def additionalFactorsJsonArray(
      additionalFactors: List[AdditonalFactors]
    ): JsArray = {
      additionalFactors.foldLeft[JsArray](Json.arr()) { (a, b) =>
        val jsObject = if (b.factorType == "totp") {
          Json.obj(
            ("factorType", JsString(b.factorType)),
            ("name", JsString(b.name.getOrElse("")))
          )
        } else {
          Json.obj(
            ("factorType", JsString(b.factorType)),
            ("phoneNumber", JsString(b.phoneNumber.getOrElse("")))
          )
        }
        a.append(jsObject)
      }
    }

    Json.obj(
      ("obfuscatedUserId", JsString(usersGroupResponse.obfuscatedUserId)),
      ("email", JsString(usersGroupResponse.email.get)),
      (
        "lastAccessedTimestamp",
        JsString(
          usersGroupResponse.lastAccessedTimestamp.format(datetimeFormatter)
        )
      )
    ) ++ usersGroupResponse.additionalFactors.fold(Json.obj()) {
      additionalFactors =>
        Json.obj(
          ("additionalFactors", additionalFactorsJsonArray(additionalFactors))
        )
    }
  }
}
