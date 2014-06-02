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
import com.alpine.rconnector.messages.{ RException, RResponse, RRequest }
import akka.event.Logging
import org.rosuda.REngine.REXP
import scala.collection.JavaConversions._

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

    case RRequest(rScript: String, returnSet: java.util.Set[String]) => {

      log.info(s"In RServeActor: received request from client through the router")
      clearWorkspace() // clear now in case previous user R code execution failed

      def eval(s: String) = conn.eval(s).asInstanceOf[REXP].asNativeJavaObject

      val results = returnSet.map(elem => (elem, eval(elem))).toMap

      println(s"\n\n\n${results.getClass.getName}: ${results.toString}\n\n\n")

      sender ! RResponse(results)

      clearWorkspace() // clean up after execution
      log.info(s"In RServeActor: done with R request, sent response")

    }

    case other => throw new AkkaException(s"Unexpected message of type ${other.getClass.getName} from $sender")

  }

  def convertRExp(): Unit = {

  }

}