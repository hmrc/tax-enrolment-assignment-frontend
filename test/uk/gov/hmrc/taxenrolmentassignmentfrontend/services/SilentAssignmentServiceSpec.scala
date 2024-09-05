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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import org.mockito.MockitoSugar.mock
import play.api.Application
import play.api.inject.{Binding, bind}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{EACDConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserEnrolmentsListResponse
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository

class SilentAssignmentServiceSpec extends BaseSpec {

  lazy val mockTaxEnrolmentsConnector: TaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]
  lazy val mockEacdConnector: EACDConnector = mock[EACDConnector]
  override val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  override lazy val overrides: Seq[Binding[JourneyCacheRepository]] = Seq(
    bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[TaxEnrolmentsConnector].toInstance(mockTaxEnrolmentsConnector),
      bind[EACDConnector].toInstance(mockEacdConnector)
    )
    .build()

  val service: SilentAssignmentService = app.injector.instanceOf[SilentAssignmentService]

  val businessEnrolmentResponse: UserEnrolmentsListResponse =
    UserEnrolmentsListResponse(Seq(userEnrolmentIRPAYE))
  val irsaResponse: UserEnrolmentsListResponse = UserEnrolmentsListResponse(
    Seq(userEnrolmentIRSA)
  )
}
