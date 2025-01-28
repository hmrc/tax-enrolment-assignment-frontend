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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.logging

import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.{Debug, Error, Info, LoggingEvent, Warn}

import javax.inject.Singleton

@Singleton
class EventLoggerService {

  def logEvent(event: LoggingEvent)(implicit logger: Logger): Unit =
    event match {
      case Debug(e) => logger.debug(s"${Json.toJson(e)}")
      case Info(e)  => logger.info(s"${Json.toJson(e)}")
      case Warn(e)  => logger.warn(s"${Json.toJson(e)}")
      case Error(e) => logger.error(s"${Json.toJson(e)}")
    }

  def logEvent(event: LoggingEvent, throwable: Throwable)(implicit
    logger: Logger
  ): Unit = event match {
    case Debug(e) => logger.debug(s"${Json.toJson(e)}", throwable)
    case Info(e)  => logger.info(s"${Json.toJson(e)}", throwable)
    case Warn(e)  => logger.warn(s"${Json.toJson(e)}", throwable)
    case Error(e) => logger.error(s"${Json.toJson(e)}", throwable)
  }

}
