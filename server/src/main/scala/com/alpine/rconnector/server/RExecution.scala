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

package com.alpine.rconnector.server

import org.rosuda.REngine.REXP
import akka.event.LoggingAdapter
import org.rosuda.REngine.Rserve.RConnection

object RExecution {

  type RScriptEvaluator = String => java.lang.Object

  def eval(conn: RConnection, log: LoggingAdapter)(s: String): java.lang.Object = {

    val enrichedScript =
      s"""try({
                         $s
                       },
                       silent=TRUE)""".stripMargin
    log.info(s"\nEvaluating $enrichedScript\n")

    val res: REXP = conn.parseAndEval(enrichedScript) // eval

    if (res.inherits("try-error")) {
      log.info(s"Error in script:\n$s")
      throw new RuntimeException(res.asString)
    }
    res.asNativeJavaObject
  }

}