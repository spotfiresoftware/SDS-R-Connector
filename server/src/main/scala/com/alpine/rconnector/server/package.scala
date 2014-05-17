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

  def logFailure(t: Throwable)(implicit log: LoggingAdapter): Unit = {
    log.info(failure(t))
  }

  def failure(t: Throwable, text: String = "Error"): String = {

    s"""$text: ${t.getClass.getName}\nCause: ${t.getCause}\nMessage: ${t.getMessage}\nStack trace: ${t.getStackTrace.mkString("\n")}\n"""
  }

  def logActorStart(clazz: Any)(implicit log: LoggingAdapter): Unit = {

    log.info(s"\n\nStarting ${clazz.getClass.getSimpleName}\n\n")
  }

}
