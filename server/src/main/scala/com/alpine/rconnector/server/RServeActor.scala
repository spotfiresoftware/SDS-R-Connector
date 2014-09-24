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

import org.rosuda.REngine.Rserve.RConnection
import akka.AkkaException
import akka.actor.Actor
import com.alpine.rconnector.messages._
import akka.event.Logging
import org.rosuda.REngine.REXP
import org.rosuda.REngine.Rserve.RserveException
import scala.collection.JavaConversions._
import com.alpine.rconnector.messages.RException
import com.alpine.rconnector.messages.RResponse
import com.alpine.rconnector.messages.RRequest
import com.alpine.rconnector.messages.FinishRSession
import java.util.{ Map => JMap }
import scala.collection.mutable.{ HashMap => MutableHashMap }
import scala.sys.process._

/**
 * This is the actor that establishes a connection to R via Rserve
 * (see <a href="http://rforge.net/Rserve/">Rserve documentation</a>).
 * <br>
 * TCP connections to R are kept for as long as the actor is alive.
 * If the actor is killed by its supervisor or throws an exception, the connection
 * gets released.
 */
class RServeActor extends Actor {

  private[this] implicit val log = Logging(context.system, this)

  protected[this] var conn: RConnection = _
  protected[this] var pid: Int = _

  def updateConnAndPid() = {

    conn = new RConnection()
    pid = conn.eval("Sys.getpid()").asNativeJavaObject.asInstanceOf[Array[Int]](0)
  }

  override def preStart(): Unit = updateConnAndPid()

  // Map of datasetUuid -> sessionUuid
  private[this] val txMap = MutableHashMap[String, String]()

  logActorStart(this)

  private def killRProcess(): Int = s"kill -9 $pid" !

  override def postStop(): Unit = killRProcess()
  // conn.close()

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    // send message about exception to the client (e.g. for UI reporting)
    sender ! RException(failure(t = reason, text = "R Server Error"))
    killRProcess()
    //conn.close()
    super.preRestart(reason, message)
  }

  // that's the default anyway, but you can do something different
  override def postRestart(reason: Throwable): Unit = preStart()

  // remove all temporary data from R workspace
  protected[this] def clearWorkspace() = conn.eval("rm(list = ls())")

  def receive: Receive = {

    case RRequest(clientUUID, rScript, returnSet: Array[String]) => {

      def eval(s: String): java.lang.Object = {
        val enrichedScript =

          s"""try({
                         $s
                       },
                       silent=TRUE)""".stripMargin
        //  log.info(s"\n\enrichedScript:\n\n$enrichedScript\n\n")
        log.info(s"\n\nEvaluating $enrichedScript\n\n")
        val res: REXP = conn.parseAndEval(enrichedScript) // eval
        if (res.inherits("try-error")) {
          throw new RserveException(conn, res.asString)
        }
        res.asNativeJavaObject
      }

      // evaluate R script
      eval(rScript)

      val results = returnSet.map(elem => (elem, eval(elem))).toMap

      log.info(s"\n\nRESULTS: $results")
      log.info(s"\n\n${results.getClass.getName}: ${results.toString}\n\n")

      sender ! RResponse(results)

      log.info(s"In RServeActor: done with R request, sent response")

    }

    case RAssign(clientUUID: String, dataFrames: JMap[_, _]) => {

      // asInstanceOf[JMap[String, String]].foreach { elem => conn.assign(elem._1, elem._2) }
      dataFrames.foreach { elem =>
        elem match {
          case (x, y: String) => conn.assign(x, y)
          case (x, y: Array[Byte]) => conn.assign(x, y)
          case (x, y) => throw new IllegalStateException(
            s"Unsupported type of value $x Expected either String or Array[Byte] but got ${y.getClass.getName}"
          )
        }
      }
      log.info(s"""\n\nAssigned the following variables to the R workspace for session $clientUUID:\n
                   ${dataFrames.keySet} """)
      sender ! AssignAck(clientUUID, dataFrames.keySet.map(_.toString).toArray)
    }

    case StartTx(sessionUuid, datasetUuid, columnNames) => {
      txMap += datasetUuid -> sessionUuid
      // open file connection
      sender ! StartTxAck(sessionUuid, datasetUuid)
    }

    case EndTx(sessionUuid, datasetUuid) => {
      txMap -= datasetUuid
      // close file connection and mark the data as being OK to be read from R
      // (note the path to enrich the R script)
      sender ! EndTx(sessionUuid, datasetUuid)
    }

    case DelimitedPacket(sessionUuid, datasetUuid, packetUuid, payload, delimiter) => {
      // write packet to disk
    }

    case FinishRSession(uuid) => {

      log.info(s"Finishing R session for UUID $uuid")
      killRProcess()
      updateConnAndPid()
      sender ! RSessionFinishedAck(uuid)
    }

    case ClientHeartbeatRequest(uuid) => {
      log.info(s"\nSending heartbeat response to $uuid\n")
      sender ! ServerHeartbeatReponse(uuid)
    }

    case other => throw new AkkaException(s"Unexpected message of type ${other.getClass.getName} from $sender")

  }

}