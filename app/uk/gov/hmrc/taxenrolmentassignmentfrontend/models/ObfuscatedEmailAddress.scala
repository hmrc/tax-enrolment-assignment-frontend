/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models

import scala.util.matching.Regex
import scala.language.implicitConversions

trait ObfuscatedEmailAddress {
  val value: String
  override def toString: String = value
}

object ObfuscatedEmailAddress {
  final private val splitMailbox: Regex = "(.)(.*)(.)".r
  val validEmail: Regex = """^([a-zA-Z0-9.!#$%&’'*+/=?^_`{|}~@-]+)@([a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*)$""".r

  implicit def obfuscatedEmailToString(e: ObfuscatedEmailAddress): String = e.value

  def apply(plainEmailAddress: String): ObfuscatedEmailAddress =
    new ObfuscatedEmailAddress {
      val value: String = plainEmailAddress match {
        case validEmail(m, domain) if m.length < 3 =>
          s"${obscure(m)}@$domain"

        case validEmail(splitMailbox(firstLetter, middle, lastLetter), domain) =>
          s"$firstLetter${obscure(middle)}$lastLetter@$domain"

        case invalidEmail =>
          throw new IllegalArgumentException(s"Cannot obfuscate invalid email address '$invalidEmail'")
      }
    }

  private def obscure(text: String): String = "*" * text.length
}
