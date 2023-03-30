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

package services

import helpers.IntegrationSpecBase
import helpers.TestITData.{CREDENTIAL_ID, accountDetailsUnUserFriendly, usersGroupSearchResponse}
import play.api.libs.json.JsObject
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.PT_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.UsersGroupsSearchService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails

class UserGroupSearchServiceISpec extends IntegrationSpecBase {

  lazy val service = app.injector.instanceOf[UsersGroupsSearchService]

  s"getAccountDetails" should {
    s"userGroupSearch response in mongo and email should be encrypted" in {

      stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
      val request = requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER)
      val res = service.getAccountDetails(CREDENTIAL_ID)(implicitly,implicitly,request)

      whenReady(res.value) { response =>
        response shouldBe Right(accountDetailsUnUserFriendly(CREDENTIAL_ID))
        response.getOrElse(AccountDetails("", "", None, "", Seq.empty, None)).emailDecrypted shouldBe Some("email1@test.com")

      }
     val emailEncrypted: String = {
       ((sessionRepository.get(request.sessionID)).futureValue.get.data.get("AccountDetailsFor6902202884164548").get.as[JsObject] \ "email").as[String]
     }

      crypto.crypto.decrypt(Crypted(emailEncrypted)).value shouldBe """"email1@test.com""""
    }
  }







}
