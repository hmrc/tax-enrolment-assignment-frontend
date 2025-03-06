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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

class EventLoggerServiceSpec extends BaseSpec {

  def withCaptureOfLoggingFrom(
    logger: LoggerLike
  )(body: (=> List[ILoggingEvent]) => Unit): Unit =
    withCaptureOfLoggingFrom(logger.logger.asInstanceOf[LogbackLogger])(body)

  def withCaptureOfLoggingFrom[T](
    body: (=> List[ILoggingEvent]) => Unit
  )(implicit classTag: ClassTag[T]): Unit =
    withCaptureOfLoggingFrom(Logger(classTag.runtimeClass))(body)

  def withCaptureOfLoggingFrom(
    logger: LogbackLogger
  )(body: (=> List[ILoggingEvent]) => Unit): Unit = {
    val appender = new ListAppender[ILoggingEvent]()
    appender.setContext(logger.getLoggerContext)
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.TRACE)
    logger.setAdditive(true)
    body(appender.list.asScala.toList)
  }

  val eventLogger: EventLoggerService = app.injector.instanceOf[EventLoggerService]

  implicit val testLogger: Logger = Logger("test-logger")

  "logEvent" when {
    "a info level log is received with no exception" should {
      "log and return unit" in {
        val infoLevelEvent =
          Info(Event("test event", details = Some("level INFO"), None))
        withCaptureOfLoggingFrom(testLogger) { events =>
          eventLogger.logEvent(infoLevelEvent)
          val expected =
            """{"event":"test event","details":"level INFO"}"""
          events
            .collectFirst { case event =>
              event.getLevel.levelStr shouldEqual "INFO"
              event.getThrowableProxy shouldEqual null
              event.getMessage shouldEqual expected
            }
            .getOrElse(fail("No logging captured"))
        }
      }
    }

    "a info level log is received with an exception" should {
      "log and return unit" in {
        val infoLevelEvent =
          Info(Event("test event", details = Some("level INFO")))
        val exception = new Exception("info error")
        withCaptureOfLoggingFrom(testLogger) { events =>
          eventLogger.logEvent(infoLevelEvent, exception)
          val expected =
            """{"event":"test event","details":"level INFO"}"""
          events
            .collectFirst { case event =>
              event.getLevel.levelStr shouldEqual "INFO"
              event.getThrowableProxy.getMessage shouldEqual "info error"
              event.getMessage shouldEqual expected
            }
            .getOrElse(fail("No logging captured"))
        }
      }
    }

    "a warn level log is received with no exception" should {
      "log and return unit" in {
        val infoLevelEvent =
          Warn(Event("test event", errorDetails = Some("level WARN")))
        withCaptureOfLoggingFrom(testLogger) { events =>
          eventLogger.logEvent(infoLevelEvent)
          val expected =
            """{"event":"test event","errorDetails":"level WARN"}"""
          events
            .collectFirst { case event =>
              event.getLevel.levelStr shouldEqual "WARN"
              event.getThrowableProxy shouldEqual null
              event.getMessage shouldEqual expected
            }
            .getOrElse(fail("No logging captured"))
        }
      }
    }

    "a warn level log is received with an exception" should {
      "log and return unit" in {
        val infoLevelEvent =
          Warn(Event("test event", errorDetails = Some("level WARN")))
        val exception = new Exception("warn error")
        withCaptureOfLoggingFrom(testLogger) { events =>
          eventLogger.logEvent(infoLevelEvent, exception)
          val expected =
            """{"event":"test event","errorDetails":"level WARN"}"""
          events
            .collectFirst { case event =>
              event.getLevel.levelStr shouldEqual "WARN"
              event.getThrowableProxy.getMessage shouldEqual "warn error"
              event.getMessage shouldEqual expected
            }
            .getOrElse(fail("No logging captured"))
        }
      }
    }

    "a error level log is received with no exception" should {
      "log and return unit" in {
        val infoLevelEvent =
          Error(Event("test event", errorDetails = Some("level ERROR")))
        withCaptureOfLoggingFrom(testLogger) { events =>
          eventLogger.logEvent(infoLevelEvent)
          val expected =
            """{"event":"test event","errorDetails":"level ERROR"}"""
          events
            .collectFirst { case event =>
              event.getLevel.levelStr shouldEqual "ERROR"
              event.getThrowableProxy shouldEqual null
              event.getMessage shouldEqual expected
            }
            .getOrElse(fail("No logging captured"))
        }
      }
    }

    "a error level log is received with an exception" should {
      "log and return unit" in {
        val infoLevelEvent =
          Error(Event("test event", errorDetails = Some("level ERROR")))
        val exception = new Exception("error")
        withCaptureOfLoggingFrom(testLogger) { events =>
          eventLogger.logEvent(infoLevelEvent, exception)
          val expected =
            """{"event":"test event","errorDetails":"level ERROR"}"""
          events
            .collectFirst { case event =>
              event.getLevel.levelStr shouldEqual "ERROR"
              event.getThrowableProxy.getMessage shouldEqual "error"
              event.getMessage shouldEqual expected
            }
            .getOrElse(fail("No logging captured"))
        }
      }
    }
  }
}
