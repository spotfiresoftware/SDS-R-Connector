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

import akka.actor._
import akka.event.Logging
import com.alpine.rconnector.messages._
import akka.routing.RoundRobinRouter
import com.alpine.rconnector.messages.RRequest
import scala.collection.mutable.HashMap

/**
 * This class does the routing of requests from clients to the RServeActor, which then
 * talks directly to R. It also supervises the RServeActors, restarting them in case of
 * connection failures, resumes them in case of R code evaluation failures (bugs in user code), etc.
 */
class RServeMaster extends Actor {

  private[this] implicit val log = Logging(context.system, this)

  logActorStart(this)

  // TODO: base it off of application.conf
  private[this] val numRoutees = 4

  protected[this] var rServeRouter: Vector[ActorRef] = createRServeRouter()

  protected[this] def createRServeRouter(): Vector[ActorRef] =
    Vector.fill(numRoutees)(context.actorOf(Props[RServeActorSupervisor]))

  private[this] val sessionMap = new HashMap[String, ActorRef]

  private[this] var currentRoutee = 0

  private[this] def resolveActor(uuid: String): ActorRef = {

    if (sessionMap.contains(uuid)) {

      log.info(s"Found actor responsible for session $uuid - it is ${sessionMap(uuid)}")
      println(s"Found actor responsible for session $uuid - it is ${sessionMap(uuid)}")
      sessionMap(uuid)

    } else {

      currentRoutee = (currentRoutee + 1) % numRoutees
      val routeeRef = rServeRouter(currentRoutee)

      log.info(s"Assigning new actor for session $uuid - currentRoutee = $currentRoutee")
      log.info(s"ActorRef in question is $routeeRef")
      println(s"Assigning new actor for session $uuid - currentRoutee = $currentRoutee")
      println(s"ActorRef in question is $routeeRef")

      sessionMap += (uuid -> routeeRef)
      routeeRef
    }
  }

  def receive: Receive = {

    case x @ RRequest(uuid, _, _) => {

      log.info(s"\n\nRServeMaster: received RRequest request and routing it to RServe actor\n\n")
      println(s"\n\nRServeMaster: received RRequest request and routing it to RServe actor\n\n")

      resolveActor(uuid).tell(x, sender)
    }

    case x @ RAssign(uuid: String, _) => {

      log.info(s"\n\nRServeMaster: received RAssign request and routing it to RServe actor\n\n")
      println(s"\n\nRServeMaster: received RAssign request and routing it to RServe actor\n\n")

      resolveActor(uuid).tell(x, sender)
    }

    case RStart => {

      log.info(s"\n\nMaster: Starting router\n\n")

      rServeRouter = createRServeRouter()
      sender ! StartAck
    }

    case RStop => {

      // TODO: need to shutdown RServeMaster, and this should be handled by its newly created supervisor
      log.info(s"\n\nMaster: Stopping router\n\n")

      // rServeRouter ! PoisonPill
      sender ! StopAck
    }

    case x @ FinishRSession(uuid) => {

      log.info(s"\n\nFinishing R session for client ID $uuid\n\n")
      println(s"\n\nFinishing R session for client ID $uuid\n\n")

      sessionMap(uuid) ! x
      sessionMap -= uuid
    }
  }

}
