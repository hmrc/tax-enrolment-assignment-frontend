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

import cats.data.EitherT
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{Format, JsBoolean, Json}
import play.api.mvc.{AnyContent, BodyParsers}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{BaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{EACDService, SilentAssignmentService, UsersGroupsSearchService}

import scala.concurrent.{ExecutionContext, Future}

class MultipleAccountsOrchestratorSpec extends BaseSpec {

  lazy val mockSilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockEacdService = mock[EACDService]
  lazy val mockTeaSessionCache = mock[TEASessionCache]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]
  lazy val mockUsersGroupService = mock[UsersGroupsSearchService]

  override lazy val overrides = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[EACDService].toInstance(mockEacdService),
      bind[UsersGroupsSearchService].toInstance(mockUsersGroupService)
    )
    .build()

  val orchestrator = app.injector.instanceOf[MultipleAccountsOrchestrator]

  s"getDetailsForEnrolledPT" when {
    List(SINGLE_OR_MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER).foreach { accountType =>
      s"the account type is correct for $accountType" should {
        "return the userdetails for the account" in {
          (mockUsersGroupService
            .getAccountDetails(_: String)(
              _: ExecutionContext,
              _: HeaderCarrier,
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
            ))
            .expects(CREDENTIAL_ID, *, *, *)
            .returning(createInboundResult(accountDetails))

          val res = orchestrator.getDetailsForEnrolledPT(requestWithAccountType(accountType), implicitly, implicitly)
          whenReady(res.value) { result =>
            result shouldBe Right(
              accountDetails
                .copy(hasSA = Some(accountType == SA_ASSIGNED_TO_CURRENT_USER))
            )
          }
        }
      }
    }

    List(
      PT_ASSIGNED_TO_OTHER_USER,
      PT_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the accountType is $accountType" should {
        s"return the $IncorrectUserType containing redirectUrl" in {
          val res = orchestrator.getDetailsForEnrolledPT(requestWithAccountType(accountType), implicitly, implicitly)
          whenReady(res.value) { result =>
            result shouldBe Left(IncorrectUserType(UrlPaths.returnUrl, accountType))
          }
        }
      }
    }
  }

  s"getDetailsForEnrolledPTWithSAOnOtherAccount" when {
    s"the accountType $SA_ASSIGNED_TO_OTHER_USER " should {
      "return the userdetails for the account" in {
        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID, *, *, *)
          .returning(createInboundResult(accountDetails))

        val res = orchestrator.getDetailsForEnrolledPTWithSAOnOtherAccount(
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER),
          implicitly,
          implicitly
        )
        whenReady(res.value) { result =>
          result shouldBe Right(accountDetails)
        }
      }
    }
    List(
      PT_ASSIGNED_TO_OTHER_USER,
      PT_ASSIGNED_TO_CURRENT_USER,
      SINGLE_OR_MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the accountType is $accountType" should {
        s"return the $IncorrectUserType" in {
          val res = orchestrator.getDetailsForEnrolledPTWithSAOnOtherAccount(
            requestWithAccountType(accountType),
            implicitly,
            implicitly
          )
          whenReady(res.value) { result =>
            result shouldBe Left(IncorrectUserType(UrlPaths.returnUrl, accountType))
          }
        }
      }
    }
  }

  s"getCurrentAndPTAAndSAIfExistsForUser" when {
    s"the accountType $PT_ASSIGNED_TO_OTHER_USER, no SA associated to the account" should {
      "return a PTEnrolmentOtherAccountViewModel for the account details" in {

        val additionalCacheData = Map("USER_ASSIGNED_PT_ENROLMENT" -> Json.toJson(UsersAssignedEnrolment1))
        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID, *, *, *)
          .returning(createInboundResult(accountDetails))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID_1, *, *, *)
          .returning(createInboundResult(accountDetailsWithPT.copy(hasSA = None)))

        (mockEacdService
          .getUsersAssignedSAEnrolment(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(UsersAssignedEnrolment(None)))

        val res = orchestrator
          .getCurrentAndPTAAndSAIfExistsForUser(
            requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
            implicitly,
            implicitly
          )
        whenReady(res.value) { result =>
          result shouldBe Right(ptEnrolmentDataModel(None, accountDetailsWithPT.copy(hasSA = None)))
        }
      }
    }

    s"the accountType $PT_ASSIGNED_TO_OTHER_USER, account has SA in the current session" should {
      "return a PTEnrolmentOtherAccountViewModel for the account details" in {
        val additionalCacheData = Map("USER_ASSIGNED_PT_ENROLMENT" -> Json.toJson(UsersAssignedEnrolment1))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID, *, *, *)
          .returning(createInboundResult(accountDetails))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID_1, *, *, *)
          .returning(createInboundResult(accountDetailsWithPT))

        (mockEacdService
          .getUsersAssignedSAEnrolment(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(UsersAssignedEnrolment(Some(CREDENTIAL_ID))))

        val res = orchestrator.getCurrentAndPTAAndSAIfExistsForUser(
          requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
          implicitly,
          implicitly
        )
        whenReady(res.value) { result =>
          result shouldBe Right(ptEnrolmentDataModel(Some(USER_ID)))
        }
      }
    }

    s"the accountType $PT_ASSIGNED_TO_OTHER_USER, account has SA on the another account that also has PT" should {
      "return a PTEnrolmentOtherAccountViewModel for the account details" in {
        val additionalCacheData = Map("USER_ASSIGNED_PT_ENROLMENT" -> Json.toJson(UsersAssignedEnrolment1))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID, *, *, *)
          .returning(createInboundResult(accountDetails))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID_1, *, *, *)
          .returning(createInboundResult(accountDetailsWithPT))

        (mockEacdService
          .getUsersAssignedSAEnrolment(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(UsersAssignedEnrolment(Some(CREDENTIAL_ID_1))))

        val res = orchestrator.getCurrentAndPTAAndSAIfExistsForUser(
          requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
          implicitly,
          implicitly
        )
        whenReady(res.value) { result =>
          result shouldBe Right(ptEnrolmentDataModel(Some(PT_USER_ID)))
        }
      }
    }

    s"the accountType $PT_ASSIGNED_TO_OTHER_USER, the account has SA on another account with a third account that holds the PT enrolment" should {
      "return a PTEnrolmentOtherAccountViewModel for the account details" in {
        val additionalCacheData = Map("USER_ASSIGNED_PT_ENROLMENT" -> Json.toJson(UsersAssignedEnrolment1))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID, *, *, *)
          .returning(createInboundResult(accountDetails))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID_1, *, *, *)
          .returning(createInboundResult(accountDetailsWithPT))

        (mockEacdService
          .getUsersAssignedSAEnrolment(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(UsersAssignedEnrolment(Some(CREDENTIAL_ID_2))))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID_2, *, *, *)
          .returning(createInboundResult(accountDetails.copy(userId = CREDENTIAL_ID_2)))

        val res = orchestrator.getCurrentAndPTAAndSAIfExistsForUser(
          requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
          implicitly,
          implicitly
        )
        whenReady(res.value) { result =>
          result shouldBe Right(ptEnrolmentDataModel(Some(CREDENTIAL_ID_2)))
        }
      }
    }

    List(
      PT_ASSIGNED_TO_CURRENT_USER,
      SINGLE_OR_MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_CURRENT_USER,
      SA_ASSIGNED_TO_OTHER_USER
    ).foreach { accountType =>
      s"the accountType is $accountType" should {
        s"return the $IncorrectUserType containing redirectUrl" in {

          val res = orchestrator.getCurrentAndPTAAndSAIfExistsForUser(
            requestWithAccountType(accountType),
            implicitly,
            implicitly
          )
          whenReady(res.value) { result =>
            result shouldBe Left(IncorrectUserType((UrlPaths.returnUrl), accountType))
          }
        }
      }
    }
  }

  "checkValidAccountTypeAndEnrolForPT" when {
    for (inputAccountType <- all_account_types)
      for (sessionAccountType <- all_account_types)
        s"the request has an accountType of ${sessionAccountType.toString} and ${inputAccountType.toString} is required" should {
          if (sessionAccountType == inputAccountType) {
            "return unit and enrol user for PT" in {

              (mockSilentAssignmentService
                .enrolUser()(
                  _: RequestWithUserDetailsFromSession[_],
                  _: HeaderCarrier,
                  _: ExecutionContext
                ))
                .expects(*, *, *)
                .returning(
                  EitherT.right[TaxEnrolmentAssignmentErrors](
                    Future.successful(())
                  )
                )

              val res = orchestrator.checkValidAccountTypeAndEnrolForPT(
                inputAccountType
              )(requestWithAccountType(sessionAccountType), implicitly, implicitly)

              whenReady(res.value) { result =>
                result shouldBe Right(())
              }
            }
          } else {
            s"return $IncorrectUserType" in {

              val res = orchestrator.checkValidAccountTypeAndEnrolForPT(
                inputAccountType
              )(requestWithAccountType(sessionAccountType), implicitly, implicitly)

              whenReady(res.value) { result =>
                result shouldBe Left(IncorrectUserType(UrlPaths.returnUrl, sessionAccountType))
              }
            }
          }
        }
  }

  "getSACredentialIfNotFraud" when {
    "the user has reported fraud" should {
      "return None" in {
        val additionalCacheData = Map("reportedFraud" -> JsBoolean(true))

        val res = orchestrator.getSACredentialIfNotFraud(
          requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
          implicitly,
          implicitly
        )
        whenReady(res.value) { result =>
          result shouldBe Right(None)
        }
      }
    }

    "the user has not reported fraud" should {
      "return the account details for the SA user" when {
        "the sa user is available in the cache" in {
          val additionalCacheData = Map("USER_ASSIGNED_SA_ENROLMENT" -> Json.toJson(UsersAssignedEnrolment1))

          (mockUsersGroupService
            .getAccountDetails(_: String)(
              _: ExecutionContext,
              _: HeaderCarrier,
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
            ))
            .expects(CREDENTIAL_ID_1, *, *, *)
            .returning(createInboundResult(accountDetails))

          val res = orchestrator.getSACredentialIfNotFraud(
            requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
            implicitly,
            implicitly
          )

          whenReady(res.value) { result =>
            result shouldBe Right(Some(accountDetails))
          }
        }
      }

      "return NoSAEnrolmentWhenOneExpected" when {
        "the sa user in the cache is empty" in {
          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: RequestWithUserDetailsFromSession[_],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))
          val additionalCacheData = Map("USER_ASSIGNED_SA_ENROLMENT" -> Json.toJson(UsersAssignedEnrolmentEmpty))

          val res = orchestrator.getSACredentialIfNotFraud(
            requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
            implicitly,
            implicitly
          )

          whenReady(res.value) { result =>
            result shouldBe Left(NoSAEnrolmentWhenOneExpected)
          }
        }

        "the cache is empty" in {
          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: RequestWithUserDetailsFromSession[_],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          val res =
            orchestrator.getSACredentialIfNotFraud(requestWithAccountType(randomAccountType), implicitly, implicitly)

          whenReady(res.value) { result =>
            result shouldBe Left(NoSAEnrolmentWhenOneExpected)
          }
        }
      }
    }
  }

  "getPTCredentialDetails" when {
    "a pt enrolment exists for a different credential" should {
      "return the account details for the PT user" in {
        val additionalCacheData = Map("USER_ASSIGNED_PT_ENROLMENT" -> Json.toJson(UsersAssignedEnrolment1))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(CREDENTIAL_ID_1, *, *, *)
          .returning(createInboundResult(accountDetails))

        val res = orchestrator.getPTCredentialDetails(
          requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
          implicitly,
          implicitly
        )

        whenReady(res.value) { result =>
          result shouldBe Right(accountDetails)
        }
      }
    }

    "a pt enrolment exists for the signed in credential" should {
      "return NoPTEnrolmentWhenOneExpected" in {
        val additionalCacheData = Map("USER_ASSIGNED_PT_ENROLMENT" -> Json.toJson(UsersAssignedEnrolmentCurrentCred))

        val res = orchestrator.getPTCredentialDetails(
          requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
          implicitly,
          implicitly
        )

        whenReady(res.value) { result =>
          result shouldBe Left(NoPTEnrolmentWhenOneExpected)
        }
      }
    }

    "no pt enrolment exists" should {
      "return NoPTEnrolmentWhenOneExpected" in {
        val additionalCacheData = Map("USER_ASSIGNED_PT_ENROLMENT" -> Json.toJson(UsersAssignedEnrolmentEmpty))

        val res = orchestrator.getPTCredentialDetails(
          requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
          implicitly,
          implicitly
        )

        whenReady(res.value) { result =>
          result shouldBe Left(NoPTEnrolmentWhenOneExpected)
        }
      }
    }

    "the cache is empty" should {
      "return NoPTEnrolmentWhenOneExpected" in {

        val res = orchestrator.getPTCredentialDetails(requestWithAccountType(randomAccountType), implicitly, implicitly)

        whenReady(res.value) { result =>
          result shouldBe Left(NoPTEnrolmentWhenOneExpected)
        }
      }
    }
  }

  "getDetailsForKeepAccessToSA" when {
    all_account_types.foreach { accountType =>
      s"the user has an account type of $accountType" should {
        if (accountType == SA_ASSIGNED_TO_OTHER_USER) {
          "return an empty form" when {
            "there is no form stored in session" in {

              val res = orchestrator.getDetailsForKeepAccessToSA(requestWithAccountType(accountType), implicitly)

              whenReady(res.value) { result =>
                result shouldBe Right(
                  KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
                )
              }
            }
          }
          "return a populated form" when {
            "there is form data stored in session" in {
              val additionalCacheData =
                Map("KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM" -> Json.toJson(KeepAccessToSAThroughPTA(true)))

              val res = orchestrator.getDetailsForKeepAccessToSA(
                requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, additionalCacheData = additionalCacheData),
                implicitly
              )
              whenReady(res.value) { result =>
                result shouldBe Right(
                  KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
                    .fill(KeepAccessToSAThroughPTA(true))
                )
              }
            }
          }
        } else {
          s"return $IncorrectUserType error" in {
            val res = orchestrator.getDetailsForKeepAccessToSA(requestWithAccountType(accountType), implicitly)

            whenReady(res.value) { result =>
              result shouldBe Left(IncorrectUserType(UrlPaths.returnUrl, accountType))
            }
          }
        }
      }
    }
  }

  "handleKeepAccessToSAChoice" when {
    all_account_types.foreach { accountType =>
      s"the user has an account type of $accountType" should {
        if (accountType == SA_ASSIGNED_TO_OTHER_USER) {
          "not enrol for PT and save form to cache" when {
            "the user choose to keep SA and PT together" in {
              (mockTeaSessionCache
                .save(_: String, _: KeepAccessToSAThroughPTA)(
                  _: RequestWithUserDetailsFromSession[AnyContent],
                  _: Format[KeepAccessToSAThroughPTA]
                ))
                .expects(
                  KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
                  KeepAccessToSAThroughPTA(true),
                  *,
                  *
                )
                .returning(
                  Future.successful(CacheMap(request.sessionID, Map()))
                )

              val res = orchestrator.handleKeepAccessToSAChoice(
                KeepAccessToSAThroughPTA(true)
              )(requestWithAccountType(accountType), implicitly, implicitly)

              whenReady(res.value) { result =>
                result shouldBe Right(true)
              }
            }
          }
          "enrol the user for PT and save form data to cache" when {
            "the user chooses to have PT and SA separate" in {
              (mockSilentAssignmentService
                .enrolUser()(
                  _: RequestWithUserDetailsFromSession[_],
                  _: HeaderCarrier,
                  _: ExecutionContext
                ))
                .expects(*, *, *)
                .returning(
                  EitherT.right[TaxEnrolmentAssignmentErrors](
                    Future.successful(())
                  )
                )
              (mockTeaSessionCache
                .save(_: String, _: KeepAccessToSAThroughPTA)(
                  _: RequestWithUserDetailsFromSession[AnyContent],
                  _: Format[KeepAccessToSAThroughPTA]
                ))
                .expects(
                  KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
                  KeepAccessToSAThroughPTA(false),
                  *,
                  *
                )
                .returning(
                  Future.successful(CacheMap(request.sessionID, Map()))
                )

              val res = orchestrator.handleKeepAccessToSAChoice(
                KeepAccessToSAThroughPTA(false)
              )(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER), implicitly, implicitly)

              whenReady(res.value) { result =>
                result shouldBe Right(false)
              }
            }
          }
        } else {
          s"return $IncorrectUserType error" in {
            val res = orchestrator.getDetailsForKeepAccessToSA(requestWithAccountType(accountType), implicitly)

            whenReady(res.value) { result =>
              result shouldBe Left(IncorrectUserType(UrlPaths.returnUrl, accountType))
            }
          }
        }
      }
    }
  }
}
