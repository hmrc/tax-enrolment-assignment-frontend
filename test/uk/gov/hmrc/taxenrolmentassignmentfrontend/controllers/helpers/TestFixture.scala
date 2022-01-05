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

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.Injector
import play.api.mvc.{AnyContent, BodyParsers, DefaultMessagesActionBuilderImpl, MessagesActionBuilder}
import play.api.test.Helpers.{stubBodyParser, stubMessagesApi, stubMessagesControllerComponents}
import play.api.test.Injecting
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction

import scala.concurrent.ExecutionContext

trait TestFixture extends AnyWordSpec with MockFactory with GuiceOneAppPerSuite with Matchers with Injecting {

  lazy val injector: Injector = app.injector
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockAuthAction = new AuthAction(mockAuthConnector, testBodyParser)

  //Controller
  val messagesActionBuilder: MessagesActionBuilder = new DefaultMessagesActionBuilderImpl(stubBodyParser[AnyContent](), stubMessagesApi())
  lazy val mcc = stubMessagesControllerComponents()


}