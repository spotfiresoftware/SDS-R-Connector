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
import com.alpine.rconnector.messages.RRequest
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.HashMap

/**
 * This class does the routing of requests from clients to the RServeActor, which then
 * talks directly to R. It also supervises the RServeActors, restarting them in case of
 * connection failures, resumes them in case of R code evaluation failures (bugs in user code), etc.
 *
 * @author Marek Kolodziej
 * @since 6/1/2014
 */
class RServeMaster extends Actor {

  private implicit val log = Logging(context.system, this)

  logActorStart(this)

  private val config = ConfigFactory.load().getConfig("rServeKernelApp")
  protected val numRoutees = config.getInt("akka.rServe.numActors")

  log.info(s"\n\nnumActors = $numRoutees\n\n")

  /* rServeRouter is a var because the RServeMaster may keep on running while
  the RServeActors may be shut down by the client by sending the RStop
  message to RServeMaster. The actors can then be restarted by having
  the client send the RStart message. */
  protected var rServeRouter: Option[Vector[ActorRef]] = createRServeRouter()

  /* Call to create the router, either upon RServeMaster start
     or to restart when the RStart message is received, after a prior
     receipt of the RStop message.
   */
  protected def createRServeRouter(): Option[Vector[ActorRef]] =
    Some(Vector.fill(numRoutees)(context.actorOf(Props[RServeActorSupervisor])))

  /* Map to hold session UUID keys and corresponding ActorRef values.
     This allows the R session to be sticky and for the messages from the client
     to be redirected to the appropriate R worker, despite the load balancing.
     This is necessary for multiple R requests to be made by the client,
     e.g. for streaming data in chunks from Java to R, and other situations
     that require the Java-R connection be to be stateful beyond a single request.
   */
  private val sessionMap = new HashMap[String, ActorRef]

  /* Find if there is a free actor and send its ActorRef, otherwise send None.
     This is necessary because there may be more Java requests to R workers
     than there are workers available.
   */
  private def resolveActor(uuid: String): Option[ActorRef] = {

    if (sessionMap.contains(uuid)) {

      log.info(s"Found actor responsible for session $uuid - it is ${sessionMap(uuid)}")
      Some(sessionMap(uuid))

    } else {

      val availableActors = rServeRouter.map(_.toSet &~ sessionMap.values.toSet)

      availableActors match {

        case Some(set) if !set.isEmpty => {

          val unusedActor = set.head
          log.info(s"\n\nFound unused actor for session $uuid - it is $unusedActor\n\n")
          sessionMap += (uuid -> unusedActor)
          Some(unusedActor)
        }

        case _ => None
      }
    }
  }

  /* The main message-processing method of the actor */
  def receive: Receive = {

    case x @ IsRActorAvailable(uuid) => {

      resolveActor(uuid) match {
        case Some(ref) => sender ! AvailableRActorFound
        case None => sender ! RActorIsNotAvailable
      }

    }

    case x @ RRequest(uuid, _, _) => {

      log.info(s"\n\nRServeMaster: received RRequest request and routing it to RServe actor\n\n")

      resolveActor(uuid) match {

        case Some(ref) => ref.tell(x, sender)
        case None => sender ! RActorIsNotAvailable
      }
    }

    case x @ RAssign(uuid, _) => {

      log.info(s"\n\nRServeMaster: received RAssign request and routing it to RServe actor\n\n")

      resolveActor(uuid) match {

        case Some(ref) => ref.tell(x, sender)
        case None => sender ! RActorIsNotAvailable
      }
    }

    /* Restart the router after it was previously stopped via RStop */
    case RStart => {

      log.info(s"\n\nMaster: Starting router\n\n")

      rServeRouter = createRServeRouter()
      sender ! StartAck
    }

    /* Stop the router, shut down the R workers, and clear the R session map */
    case RStop => {

      // TODO: need to shutdown RServeMaster, and this should be handled by its newly created supervisor
      log.info(s"\n\nMaster: Stopping router\n\n")

      rServeRouter.foreach(_.foreach(_.tell(PoisonPill, self)))
      rServeRouter = None
      sessionMap.clear()

      sender ! StopAck
    }

    case x @ StartTx(sessionUuid, datasetUuid) => {

      log.info(s"\n\nMaster: got StartTx request for session $sessionUuid for dataset $datasetUuid\n\n")
      resolveActor(sessionUuid) match {
        case Some(ref) => ref.tell(x, sender)
        case None => sender ! RActorIsNotAvailable
      }
    }

    case x @ EndTx(sessionUuid, datasetUuid) => {
      log.info(s"\n\nMaster: got EndTx request for session $sessionUuid for dataset $datasetUuid\n\n")
      resolveActor(sessionUuid) match {
        case Some(ref) => ref.tell(x, sender)
        case None => sender ! RActorIsNotAvailable
      }
    }

    case x @ CsvPacket(sessionUuid, datasetUuid, packetUuid, payload) => {
      log.info(s"\n\nMaster: got CsvPacket for session $sessionUuid for dataset $datasetUuid\n\n")
      resolveActor(sessionUuid) match {
        case Some(ref) => ref.tell(x, sender)
        case None => sender ! RActorIsNotAvailable
      }
    }

    case x @ MapPacket(sessionUuid, datasetUuid, packetUuid, payload) => {
      log.info(s"\n\nMaster: got MapPacket for session $sessionUuid for dataset $datasetUuid\n\n")
      resolveActor(sessionUuid) match {
        case Some(ref) => ref.tell(x, sender)
        case None => sender ! RActorIsNotAvailable
      }
    }

    /* Unbind an individual R session */
    case x @ FinishRSession(uuid) => {

      log.info(s"\n\nFinishing R session for client ID $uuid\n\n")

      sessionMap(uuid).tell(x, sender)
      sessionMap -= uuid
    }

    /* Get maximum number of R workers (to set the client's blocking queue size */
    case GetMaxRWorkerCount => {

      sender ! MaxRWorkerCount(numRoutees)
    }

    /* In case the client wants to know how many workers are free, e.g. for resource monitoring */
    case GetFreeRWorkerCount => {

      sender ! FreeRWorkerCount(numRoutees - sessionMap.keySet.size)
    }
  }

}
