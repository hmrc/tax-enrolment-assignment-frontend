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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators

import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.MockitoSugar.{mock, times, verify, when}
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.mvc.BodyParsers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.TaxEnrolmentsConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.ACCOUNT_TYPE
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{EACDService, SilentAssignmentService}

import scala.concurrent.Future

class AccountCheckOrchestratorSpec extends BaseSpec {

  def generateBasicCacheMap(accountType: AccountTypes.Value, redirectUrl: String = "foo"): CacheMap =
    CacheMap("id", generateBasicCacheData(accountType, redirectUrl))

  lazy val mockSilentAssignmentService: SilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockEacdService: EACDService = mock[EACDService]
  lazy val mockTeaSessionCache: TEASessionCache = mock[TEASessionCache]
  lazy val mockTaxEnrolmentConnector: TaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator: MultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]

  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[EACDService].toInstance(mockEacdService),
      bind[TaxEnrolmentsConnector].toInstance(mockTaxEnrolmentConnector)
    )
    .build()

  val orchestrator: AccountCheckOrchestrator = app.injector.instanceOf[AccountCheckOrchestrator]

  override def beforeEach(): Unit = {
    super.beforeEach()

    when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
      .thenReturn(Future.successful(None))

    when(mockEacdService.getGroupsAssignedPTEnrolment(any(), any(), any())).thenReturn(
      createInboundResult(List.empty)
    )
  }

  s"getAccountType" when {
    "the accountType is available in the cache" should {
      "return the accountType" in {
        when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
          .thenReturn(Future.successful(Some(generateBasicCacheMap(SINGLE_OR_MULTIPLE_ACCOUNTS))))

        val res = orchestrator.getAccountType
        whenReady(res.value) { result =>
          result shouldBe Right(SINGLE_OR_MULTIPLE_ACCOUNTS)
        }
      }
    }

    "a user has one credential associated with their nino" that {
      "has no PT enrolment in session or EACD" should {
        s"return SINGLE_ACCOUNT" in {

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(SINGLE_OR_MULTIPLE_ACCOUNTS))(any(), any()))
            .thenReturn(Future.successful(CacheMap(request.sessionID, Map())))

          when(mockEacdService.getGroupsAssignedPTEnrolment).thenReturn(
            createInboundResult(List.empty)
          )

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(SINGLE_OR_MULTIPLE_ACCOUNTS)
          }
        }
      }

      "has a PT enrolment in the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {

          when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(PT_ASSIGNED_TO_CURRENT_USER))(any(), any()))
            .thenReturn(Future(CacheMap(request.sessionID, Map())))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          when(mockEacdService.getGroupsAssignedPTEnrolment).thenReturn(
            createInboundResult(List.empty)
          )

          val res = orchestrator.getAccountType(
            ec,
            hc,
            requestWithEnrolments(hmrcPt = true, irSa = false)
          )

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "has PT enrolment in EACD but not the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(PT_ASSIGNED_TO_CURRENT_USER))(any(), any()))
            .thenReturn(Future(CacheMap(request.sessionID, Map())))

          when(mockEacdService.getGroupsAssignedPTEnrolment).thenReturn(
            createInboundResult(List.empty)
          )

          val res =
            orchestrator.getAccountType(implicitly, implicitly, requestWithEnrolments(hmrcPt = true, irSa = false))

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }
    }

    "a user has other none business credentials associated with their NINO" that {
      "includes one with a PT enrolment" should {
        "return PT_ASSIGNED_TO_OTHER_USER" in {

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(PT_ASSIGNED_TO_OTHER_USER))(any(), any()))
            .thenReturn(Future(CacheMap(request.sessionID, Map())))

          when(mockEacdService.getGroupsAssignedPTEnrolment).thenReturn(
            createInboundResult(List.empty)
          )

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_OTHER_USER)
          }
        }
      }

      "includes a credential (not signed in) with SA enrolment" should {
        "return SA_ASSIGNED_TO_OTHER_USER" in {

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(SA_ASSIGNED_TO_OTHER_USER))(any(), any()))
            .thenReturn(Future(CacheMap(request.sessionID, Map())))

          when(mockEacdService.getGroupsAssignedPTEnrolment).thenReturn(
            createInboundResult(List.empty)
          )

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_OTHER_USER)
          }
        }
      }

      "have no enrolments but the signed in credential has SA in request" should {
        "return SA_ASSIGNED_TO_CURRENT_USER" in {

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(SA_ASSIGNED_TO_CURRENT_USER))(any(), any()))
            .thenReturn(Future(CacheMap(request.sessionID, Map())))

          when(mockEacdService.getGroupsAssignedPTEnrolment).thenReturn(
            createInboundResult(List.empty)
          )

          val res = orchestrator.getAccountType(
            ec,
            hc,
            requestWithEnrolments(hmrcPt = false, irSa = true)
          )

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments but the signed in credential has SA in EACD" should {
        "return SA_ASSIGNED_TO_CURRENT_USER" in {

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(SA_ASSIGNED_TO_CURRENT_USER))(any(), any()))
            .thenReturn(Future(CacheMap(request.sessionID, Map())))

          val res =
            orchestrator.getAccountType(implicitly, implicitly, requestWithEnrolments(hmrcPt = false, irSa = true))

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments" should {
        s"return MULTIPLE_ACCOUNTS" in {

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(SINGLE_OR_MULTIPLE_ACCOUNTS))(any(), any()))
            .thenReturn(Future(CacheMap(request.sessionID, Map())))

          when(mockEacdService.getGroupsAssignedPTEnrolment).thenReturn(
            createInboundResult(List.empty)
          )

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(SINGLE_OR_MULTIPLE_ACCOUNTS)
          }
        }
      }

      "includes one with a SA enrolment" should {
        "return SA_ASSIGNED_TO_OTHER_USER" in {

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(SA_ASSIGNED_TO_OTHER_USER))(any(), any()))
            .thenReturn(Future(CacheMap(request.sessionID, Map())))

          when(mockEacdService.getGroupsAssignedPTEnrolment).thenReturn(
            createInboundResult(List.empty)
          )

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_OTHER_USER)
          }
        }
      }
    }
    "there are no user credentials with the enrolment" should {
      "find any groups with the assignment and deallocate them from each" in {

        when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
          .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

        when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
          .thenReturn(createInboundResult(UsersAssignedEnrolment1))

        when(mockTeaSessionCache.save(ameq(ACCOUNT_TYPE), ameq(SA_ASSIGNED_TO_OTHER_USER))(any(), any()))
          .thenReturn(Future(CacheMap(request.sessionID, Map())))

        when(mockEacdService.getGroupsAssignedPTEnrolment).thenReturn(
          createInboundResult(
            List(
              "c0506dd9-1feb-400a-bf70-6351e1ff7510",
              "c0506dd9-1feb-400a-bf70-6351e1ff7512",
              "c0506dd9-1feb-400a-bf70-6351e1ff7513"
            )
          )
        )

        val res = orchestrator.getAccountType

        whenReady(res.value) { result =>
          result shouldBe Right(SA_ASSIGNED_TO_OTHER_USER)
        }

        verify(mockEacdService, times(1)).getGroupsAssignedPTEnrolment(any(), any(), any())
        verify(mockTaxEnrolmentConnector, times(3)).deallocateEnrolment(any(), any())(any(), any())
      }
    }
  }
}
