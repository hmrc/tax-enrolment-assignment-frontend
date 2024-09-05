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

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{mock, when}
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.mvc.BodyParsers
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountTypePage, RedirectUrlPage}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{EACDService, SilentAssignmentService}

import scala.concurrent.Future

class AccountCheckOrchestratorSpec extends BaseSpec {

  def generateBasicCacheMap(accountType: AccountTypes.Value, redirectUrl: String = "foo"): CacheMap =
    CacheMap("id", generateBasicCacheData(accountType, redirectUrl))

  lazy val mockSilentAssignmentService: SilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockEacdService: EACDService = mock[EACDService]
  override val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator: MultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]

  override lazy val overrides: Seq[Binding[JourneyCacheRepository]] = Seq(
    bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[EACDService].toInstance(mockEacdService)
    )
    .build()

  val orchestrator: AccountCheckOrchestrator = app.injector.instanceOf[AccountCheckOrchestrator]

  s"getAccountType" when {
    "the accountType is available in the cache" should {
      "return the accountType" in {
        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(AccountTypePage, SINGLE_OR_MULTIPLE_ACCOUNTS.toString)
          .setOrException(RedirectUrlPage, "foo")

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        val res = orchestrator.getAccountType(Some("foo"))(
          implicitly,
          implicitly,
          requestWithGivenMongoDataAndUserAnswers(requestWithAccountType(), mockUserAnswers)
        )
        whenReady(res.value) { result =>
          result shouldBe Right(SINGLE_OR_MULTIPLE_ACCOUNTS)
        }
      }
    }

    "a user has one credential associated with their nino" that {
      "has no PT enrolment in session or EACD" should {
        s"return SINGLE_ACCOUNT" in {

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(None))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

          val res = orchestrator.getAccountType(None)

          whenReady(res.value) { result =>
            result shouldBe Right(SINGLE_OR_MULTIPLE_ACCOUNTS)
          }
        }
      }

      "has a PT enrolment in the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(None))

          when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          val res = orchestrator.getAccountType(None)(
            ec,
            hc,
            requestWithGivenSessionData(requestWithEnrolments(hmrcPt = true, irSa = false))
          )

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "has PT enrolment in EACD but not the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(None))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

          val res =
            orchestrator.getAccountType(None)(
              implicitly,
              implicitly,
              requestWithGivenSessionData(requestWithEnrolments(hmrcPt = true, irSa = false))
            )

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }
    }

    "a user has other none business credentials associated with their NINO" that {
      "includes one with a PT enrolment" should {
        "return PT_ASSIGNED_TO_OTHER_USER" in {

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(None))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

          val res = orchestrator.getAccountType(None)

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_OTHER_USER)
          }
        }
      }

      "includes a credential (not signed in) with SA enrolment" should {
        "return SA_ASSIGNED_TO_OTHER_USER" in {

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(None))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

          val res = orchestrator.getAccountType(None)

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_OTHER_USER)
          }
        }
      }

      "have no enrolments but the signed in credential has SA in request" should {
        "return SA_ASSIGNED_TO_CURRENT_USER" in {

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(None))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

          val res = orchestrator.getAccountType(None)(
            ec,
            hc,
            requestWithGivenSessionData(requestWithEnrolments(hmrcPt = false, irSa = true))
          )

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments but the signed in credential has SA in EACD" should {
        "return SA_ASSIGNED_TO_CURRENT_USER" in {

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(None))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

          val res =
            orchestrator.getAccountType(None)(
              implicitly,
              implicitly,
              requestWithGivenSessionData(requestWithEnrolments(hmrcPt = false, irSa = true))
            )

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments" should {
        s"return MULTIPLE_ACCOUNTS" in {

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(None))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

          val res = orchestrator.getAccountType(None)

          whenReady(res.value) { result =>
            result shouldBe Right(SINGLE_OR_MULTIPLE_ACCOUNTS)
          }
        }
      }

      "includes one with a SA enrolment" should {
        "return SA_ASSIGNED_TO_OTHER_USER" in {

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(None))

          when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolmentEmpty))

          when(mockEacdService.getUsersAssignedSAEnrolment(any(), any(), any()))
            .thenReturn(createInboundResult(UsersAssignedEnrolment1))

          when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

          val res = orchestrator.getAccountType(None)

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_OTHER_USER)
          }
        }
      }
    }
  }
}
