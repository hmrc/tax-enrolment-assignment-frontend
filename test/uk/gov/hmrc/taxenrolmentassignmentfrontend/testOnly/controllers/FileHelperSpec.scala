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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.controllers

import org.scalatest.matchers.must.Matchers._
import play.api.Environment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec

import java.io.IOException

class FileHelperSpec extends BaseSpec {

  private val environment = Environment.simple()
  lazy val fileHelper: FileHelper = new FileHelper(environment)

  "file exists" must {

    "Should return the parsed json response and not throw the exception" in {
      noException should be thrownBy fileHelper.loadFile("singleUserNoEnrolments.json")
    }

    "Should throw and exception when no file exists" in {
      val result = fileHelper.loadFile("dummyFileNotExists.json")

      result.failed.get shouldBe a[IOException]
    }
  }
}
