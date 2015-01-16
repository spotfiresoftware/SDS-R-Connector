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

import org.apache.commons.lang3.StringEscapeUtils._

import java.util.{ List => JList, Map => JMap }

object AlpineFileTransfer {

  private def getRTypes(eval: RExecution.RScriptEvaluator)(dfName: String = "alpine_output"): JMap[String, Array[String]] =
    eval(s"as.data.frame(lapply($dfName, class), stringsAsFactors = FALSE)").asInstanceOf[JMap[String, Array[String]]]

  private def getRNamesInOrder(eval: RExecution.RScriptEvaluator)(dfName: String = "alpine_output"): Array[String] =
    eval(s"names($dfName)").asInstanceOf[Array[String]]

  private def generateColElem(kv: (String, Array[String]), included: Boolean = true, allowEmpty: Boolean = true) =
    kv match {
      case (k: String, v: Array[String]) => {
        s"""{"columnName":"$k","columnType":"${
          v(0).toLowerCase match {
            case "integer" => "INTEGER"
            case "numeric" => "DOUBLE"
            case "logical" => "BOOLEAN"
            case "character" => "VARCHAR"
            case "factor" => "VARCHAR"
            case _ => "VARCHAR"
          }
        }","isInclude":"$included","allowEmpty":"$allowEmpty"}""".stripMargin
      }
    }

  /**
   * Obtain all of the header information about the data frame (dfName).
   */
  def getDBUploadMeta(eval: RExecution.RScriptEvaluator)(
    dfName: String = "alpine_output",
    schemaName: String,
    tableName: String,
    delimiter: String,
    quote: String,
    escape: String,
    limitNum: Long = -1,
    includeHeader: Boolean): String = {

    val getRT = getRTypes(eval)(dfName)
    val types = getRNamesInOrder(eval)(dfName)
      .map(colname => (colname, getRT.get(colname)))
      .map(kv => generateColElem(kv)).mkString(",")

    println(s"\n\ntypes\n\n$types\n\n")

    s"""{"schemaName":"$schemaName","tableName":"$tableName","delimiter":"${escapeJava(delimiter)}","quote":"${escapeJava(quote)}","escape":"${escapeJava(escape)}","limitNum":$limitNum,"includeHeader":$includeHeader,"structure":[$types]}""".stripMargin
  }

}