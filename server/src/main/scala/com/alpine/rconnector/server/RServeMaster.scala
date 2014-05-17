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

  protected[this] var rServeRouter: ActorRef = createRServeRouter()

  protected[this] def createRServeRouter(): ActorRef = {
    context.actorOf(
      Props[RServeActorSupervisor].withRouter(RoundRobinRouter(nrOfInstances = numRoutees)
      ), name = "rServeRouter")
  }

  def receive: Receive = {

    case x: RRequest => {

      log.info(s"\n\nRServeMaster: received request and routing it to RServe actor\n\n")
      rServeRouter.tell(x, sender)
    }

    case RStart => {
      log.info(s"\n\nMaster: Starting router\n\n")
      rServeRouter = createRServeRouter()
      sender ! StartAck
    }

    case RStop => {
      log.info(s"\n\nMaster: Stopping router\n\n")
      rServeRouter ! PoisonPill
      sender ! StopAck
    }
  }

}
