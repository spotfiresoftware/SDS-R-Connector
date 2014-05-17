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

import akka.actor.{ Props, Actor, OneForOneStrategy }
import org.rosuda.REngine.Rserve.RserveException
import org.rosuda.REngine.{ REXPMismatchException, REngineEvalException, REngineException }
import akka.actor.SupervisorStrategy.{ Escalate, Restart }
import scala.concurrent.duration._
import akka.event.Logging

/**
 * This actor supervises individual RServeActors. Without supervision, each RServeActor
 * would have to have its own try/catch blocks, and without handling at this level,
 * the exceptions would percolate up to the router, causing all routees to be restarted.
 */
class RServeActorSupervisor extends Actor {

  private[this] implicit val log = Logging(context.system, this)
  protected[this] val rServe = context.actorOf(Props[RServeActor])

  logActorStart(this)

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {

    case e @ (_: RserveException | _: REngineException |
      _: REngineEvalException | _: REXPMismatchException) => {
      logFailure(e)
      Restart
    }

    case _ => Escalate
  }

  def receive = {

    case msg: Any => rServe.tell(msg, sender)
  }

}
