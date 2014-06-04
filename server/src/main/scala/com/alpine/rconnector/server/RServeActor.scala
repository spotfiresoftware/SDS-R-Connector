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
import scala.collection.JavaConversions._
import com.alpine.rconnector.messages.RException
import com.alpine.rconnector.messages.RResponse
import com.alpine.rconnector.messages.RRequest
import com.alpine.rconnector.messages.FinishRSession

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

  protected[this] val conn: RConnection = new RConnection()

  logActorStart(this)

  override def postStop(): Unit = conn.close()

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    // send message about exception to the client (e.g. for UI reporting)
    sender ! RException(failure(t = reason, text = "R Server Error"))
    conn.close()
    super.preRestart(reason, message)
  }

  // that's the default anyway, but you can do something different
  override def postRestart(reason: Throwable): Unit = preStart()

  // remove all temporary data from R workspace
  protected[this] def clearWorkspace() = conn.eval("rm(list = ls())")

  def receive: Receive = {

    case RRequest(clientUUID: String, rScript: String, returnSet: java.util.List[_]) => {

      def eval(s: String) = {
        conn.eval(s).asInstanceOf[REXP].asNativeJavaObject
      }

      // first eval rScript
      log.info("\n\nEvaluating R Script\n\n")
      println("\n\nEvaluating R Script\n\n")
      conn.eval(rScript)

      //      log.info(s"\n\nIn RServeActor: received request from client through the router")
      //      log.info(s"returnSet:\n $returnSet")
      //      log.info(s"rScript:\n $rScript\n\n")
      //      log.info("\n\nAbout to evaluate ls()\n\n")
      //      log.info(s"Currently in R workspace:\n ${eval("ls()").asInstanceOf[Array[String]].toList}")

      //      println(s"In RServeActor: received request from client through the router")
      //      println(s"returnSet = $returnSet")
      //      println(s"rScript = $rScript\n\n")
      //      println("\n\nAbout to evaluate ls()\n\n")
      //      println(s"Currently in R workspace:\n ${eval("ls()").asInstanceOf[Array[String]].toList}")

      // this has to be a Java object, otherwise it won't be serializable
      val results =
        returnSet.map(_.asInstanceOf[String]).map((elem: String) => {
          //          log.info(s"Currently in R workspace:\n ${eval("ls()").asInstanceOf[Array[String]].toList}")
          //          println(s"Currently in R workspace:\n ${eval("ls()").asInstanceOf[Array[String]].toList}")
          //          log.info(s"\n\nEvaluating\n $elem\n")
          //          println(s"\n\nEvaluating\n $elem\n")
          val res = eval(elem)
          //          log.info(s"Evaluated:\n${res.asInstanceOf[Array[String]].toList}\n\n")
          //          println(s"Evaluated:\n${res.asInstanceOf[Array[String]].toList}\n\n")
          //          log.info(s"Current memory state:\n${eval("ls()").asInstanceOf[Array[String]].toList}\n\n")
          //          println(s"Current memory state:\n${eval("ls()").asInstanceOf[Array[String]].toList}\n\n")
          (elem, res)
        })

      log.info(s"\n\n\nRESULTS: $results")

      // TODO: first eval rScript, only then do the other stuff
      val javaResults: java.util.HashMap[String, Any] = new java.util.HashMap[String, Any]()
      results.foreach((pair: (String, Any)) => javaResults.put(pair._1, pair._2))

      println(s"\n\n\n${javaResults.getClass.getName}: ${javaResults.toString}\n\n\n")

      sender ! RResponse(javaResults)

      // clearWorkspace() // clean up after execution
      log.info(s"In RServeActor: done with R request, sent response")
      println(s"In RServeActor: done with R request, sent response")

    }

    case RAssign(clientUUID: String, dataFrames) => {
      // TODO: need to address the workspace clearing differently
      conn.eval("rm(list = ls())")
      dataFrames.asInstanceOf[java.util.Map[String, String]].foreach { elem: (String, String) =>
        conn.assign(elem._1, elem._2)
      }
      sender ! AssignAck(clientUUID)
    }

    case FinishRSession(uuid) => {

      log.info(s"Finishing sticky R session for UUID $uuid")
      println(s"Finishing sticky R session for UUID $uuid")

      clearWorkspace()
    }

    case other => throw new AkkaException(s"Unexpected message of type ${other.getClass.getName} from $sender")

  }

}