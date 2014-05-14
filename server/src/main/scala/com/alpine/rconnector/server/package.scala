/*
 * This file is part of Alpine Data Labs' R Connector (henceforth " R Connector").
 * R Connector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * R Connector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with R Connector.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alpine.rconnector

import akka.event.LoggingAdapter

package object server {

  def logFailure(t: Throwable)(implicit log: LoggingAdapter) = {
    log.info(s"Throwable: ${t.getClass.getName}\n")
    log.info(s"Cause: ${t.getCause}\n")
    log.info(s"Message: ${t.getMessage}\n")
    log.info(s"Stack trace: ${t.getStackTrace}\n")
  }

}
