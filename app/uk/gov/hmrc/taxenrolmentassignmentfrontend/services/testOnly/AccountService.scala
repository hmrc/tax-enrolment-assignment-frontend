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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services.testOnly

import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.testOnly.AccountConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.testOnly.{AccountDescription, SimpleEnrolment}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountService @Inject() (accountConnector: AccountConnector)(implicit
  ec: ExecutionContext
) {

  def accountSetup(accountSetupData: AccountDescription)(implicit hc: HeaderCarrier): Future[Either[String, Unit]] =
    (for {
      _ <- deleteTestData(accountSetupData)
      _ <- insertTestData(accountSetupData)
    } yield Right(())).recover { case e =>
      Left(s"Setup Failed: ${e.getMessage}")
    }

  private def deleteTestData(accountSetupData: AccountDescription)(implicit hc: HeaderCarrier): Future[Boolean] = {
    // running these futures in parallel is OK so long as the delete calls are independent
    val deleteCredId = accountConnector.deleteCredId(accountSetupData.credId)

    val enrolmentStoreDeleteKnownFact = Future.sequence(accountSetupData.knownFactsPayloads.map { payload =>
      accountConnector.deleteKnownFact(payload)
    })
    val deleteEnrolmentData = accountConnector.deleteGroupId(accountSetupData.groupId)
    val deleteFactors = accountConnector.deleteFactors(accountSetupData.credId)
    val deleteAffinity = accountConnector.deleteAffinityGroup(accountSetupData.groupId)

    for {
      _ <- deleteCredId
      _ <- enrolmentStoreDeleteKnownFact
      _ <- deleteEnrolmentData
      _ <- deleteFactors
      _ <- deleteAffinity
    } yield true
  }

  private def insertTestData(accountSetupData: AccountDescription)(implicit hc: HeaderCarrier): Future[Boolean] = {

    val accountPayload = Json.obj(
      "credId"        -> accountSetupData.credId,
      "userId"        -> accountSetupData.credId,
      "isAdmin"       -> true,
      "email"         -> "email@example.com",
      "emailVerified" -> true,
      "profile"       -> "/profile",
      "groupId"       -> accountSetupData.groupId,
      "groupProfile"  -> "/group/profile",
      "trustId"       -> "trustId",
      "name"          -> "Name",
      "suspended"     -> false
    )

    val enrolments = (accountSetupData.groupEnrolments ++ accountSetupData.userEnrolments).map {
      enrolment: SimpleEnrolment =>
        Json.obj(
          "serviceName"           -> enrolment.serviceName,
          "identifiers"           -> enrolment.identifiers,
          "enrolmentFriendlyName" -> "enrolment name",
          "assignedUserCreds"     -> Json.arr(accountSetupData.credId),
          "state"                 -> "Activated",
          "enrolmentType"         -> "principal",
          "assignedToAll"         -> true
        )
    }

    val postEnrolmentDataPayload = Json.obj(
      "groupId"       -> accountSetupData.groupId,
      "affinityGroup" -> "Individual",
      "users" -> Json.arr(
        Json.obj(
          "credId"         -> accountSetupData.credId,
          "name"           -> "Name",
          "email"          -> "email@example.com",
          "credentialRole" -> "Admin",
          "description"    -> "Description"
        )
      ),
      "enrolments" -> enrolments
    )

    for {
      _ <- accountConnector.putAccount(accountPayload)
      _ <-
        accountConnector.postEnrolmentData(postEnrolmentDataPayload).flatMap { _ =>
          Future.sequence(accountSetupData.knownFactsPayloads.map { payload =>
            accountConnector.postKnownFacts(payload)
          })
        }
      _ <-
        accountConnector.postToken(accountSetupData.postTokenPayloadMap)

      _ <- accountConnector.postMfaAccount(accountSetupData.postMfaAccountPayload)
      _ <- accountConnector.putNinoStore(accountSetupData.putNinoStorePayload, accountSetupData.credId).map(_ => ())

      _ <- accountConnector.addAffinityGroup(accountSetupData.groupId, "INDIVIDUAL")
    } yield true
  }

}
