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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import scala.util.Try


object ThrottleService {

//  def throttleBasedOnNino(nino: String, percentageToThrottle: Int): Either[Exception, Boolean] = {
//    if(nino.isEmpty || nino.length != 9) {
//      Left(new Exception(s"NINO Invalid Length or NINO empty when attempting to throttle, length: ${nino.length}"))
//    } else {
//         Try(nino.substring(6, 8).toInt)
//           .toEither
//           .left.map(_ => new Exception("NINO was not correct format when attempting to throttle"))
//           .right.map(ninoNumber => ninoNumber < percentageToThrottle)
//
//    }
//  }

}
