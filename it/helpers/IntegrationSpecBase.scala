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

package helpers

import akka.http.scaladsl.model.HttpResponse
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen, TestSuite}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.{Application, Environment, Mode}
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

trait IntegrationSpecBase extends AnyWordSpec
  with GivenWhenThen
  with TestSuite
  with ScalaFutures
  with IntegrationPatience
  with Matchers
  with WiremockHelper
  with GuiceOneServerPerSuite
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with Eventually {

  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: Int    = WiremockHelper.wiremockPort
  val mockUrl          = s"http://$mockHost:$mockPort"

  def config: Map[String, String] = Map(
    "auditing.consumer.baseUri.host"                          -> s"$mockHost",
    "auditing.consumer.baseUri.port"                          -> s"$mockPort",
    "microservice.services.auth.host"                         -> s"$mockHost",
    "microservice.services.auth.port"                         -> s"$mockPort"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout  = scaled(Span(15, Seconds)),
    interval = scaled(Span(200, Millis))
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure(config)
    .build

  override def beforeEach(): Unit =
    resetWiremock()

  override def beforeAll(): Unit = {
    super.beforeAll()
    startWiremock()
  }

  override def afterAll(): Unit = {
    stopWiremock()
    super.afterAll()
  }
}
