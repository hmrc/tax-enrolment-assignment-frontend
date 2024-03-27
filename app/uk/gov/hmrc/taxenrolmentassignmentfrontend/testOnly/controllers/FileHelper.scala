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

import play.api.Environment
import play.api.libs.json.{JsValue, Json}

import java.io.IOException
import javax.inject.{Inject, Singleton}
import scala.io.Source
import scala.util.Try

@Singleton
class FileHelper @Inject() (environment: Environment) {

  def loadFile(filePath: String): Try[JsValue] = {
    val fullPath = s"/resources/testOnly/$filePath"
    Try {
      val stream = environment
        .resourceAsStream(fullPath)
        .getOrElse(throw new IOException(s"filePath not found: $fullPath"))

      Json.parse(Source.fromInputStream(stream).mkString)
    }
  }
}
