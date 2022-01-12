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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{
  allEnrolments,
  credentials,
  nino
}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.UserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry

object TestData {

  val NINO = "testNino"
  val CREDENTIAL_ID = "credId123"
  val creds = Credentials(CREDENTIAL_ID, GovernmentGateway.toString)
  val noEnrolments = Enrolments(Set.empty[Enrolment])
  val saEnrolmentOnly = Enrolments(
    Set(
      Enrolment(
        "IR-SA",
        Seq(EnrolmentIdentifier("UTR", "123456789")),
        "Activated",
        None
      )
    )
  )
  val ptEnrolmentOnly = Enrolments(
    Set(
      Enrolment(
        "HMRC-PT",
        Seq(EnrolmentIdentifier("NINO", NINO)),
        "Activated",
        None
      )
    )
  )
  val saAndptEnrolments = Enrolments(
    Set(
      Enrolment(
        "HMRC-PT",
        Seq(EnrolmentIdentifier("NINO", NINO)),
        "Activated",
        None
      ),
      Enrolment(
        "IR-SA",
        Seq(EnrolmentIdentifier("UTR", "123456789")),
        "Activated",
        None
      )
    )
  )

  //AuthAction
  val predicates: Predicate =
    AuthProviders(GovernmentGateway) and ConfidenceLevel.L200

  val retrievals
    : Retrieval[Option[String] ~ Option[Credentials] ~ Enrolments] = nino and credentials and allEnrolments

  def retrievalResponse(
    optNino: Option[String] = Some(NINO),
    optCredentials: Option[Credentials] = Some(creds),
    enrolments: Enrolments = noEnrolments
  ): ((Option[String] ~ Option[Credentials]) ~ Enrolments) =
    new ~(new ~(optNino, optCredentials), enrolments)

  val userDetailsNoEnrolments =
    UserDetailsFromSession(CREDENTIAL_ID, NINO, false, false)
  val userDetailsWithPTEnrolment =
    UserDetailsFromSession(CREDENTIAL_ID, NINO, true, false)
  val userDetailsWithSAEnrolment =
    UserDetailsFromSession(CREDENTIAL_ID, NINO, false, true)
  val userDetailsWithPTAndSAEnrolment =
    UserDetailsFromSession(CREDENTIAL_ID, NINO, true, true)

  val ivNinoStoreEntry1 = IVNinoStoreEntry("6902202884164548", Some(50))
  val ivNinoStoreEntry2 = IVNinoStoreEntry("8316291481001919", Some(200))
  val ivNinoStoreEntry3 = IVNinoStoreEntry("0493831301037584", Some(200))
  val ivNinoStoreEntry4 = IVNinoStoreEntry("2884521810163541", Some(200))

  val multiIVCreds = List(
    ivNinoStoreEntry1,
    ivNinoStoreEntry2,
    ivNinoStoreEntry3,
    ivNinoStoreEntry4
  )

  def buildFakeRequestWithSessionId(
    method: String,
    url: String = ""
  ): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, url).withSession("sessionId" -> "FAKE_SESSION_ID")
}
