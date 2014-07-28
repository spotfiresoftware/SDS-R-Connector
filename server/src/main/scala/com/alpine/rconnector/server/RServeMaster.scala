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

import java.util.{ Timer, TimerTask }

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
  protected val timeoutMillis = config.getInt("akka.rserve.timeoutMillis")

  log.info(s"\n\nNumber of expected R workers = $numRoutees\n\n")
  log.info(s"\n\nMaximum R session duration (ms): $timeoutMillis\n\n")

  /* rServeRouter is a var because the RServeMaster may keep on running while
  the RServeActors may be shut down by the client by sending the RStop
  message to RServeMaster. The actors can then be restarted by having
  the client send the RStart message. */
  protected var rServeRouter: Option[Vector[ActorRef]] = createRServeRouter()

  /* Call to create the router, either upon RServeMaster start
     or to restart when the RStart message is received, after a prior
     receipt of the RStop message.
   */
  protected def createRServeRouter(): Option[Vector[ActorRef]] = {
    log.info(s"\nCreating R server router with $numRoutees actors")
    Some(Vector.fill(numRoutees)(context.actorOf(Props[RServeActorSupervisor])))
  }

  private def availableActors =
    rServeRouter.map(_.toSet &~ sessionMap.values.toSet)

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

      //   val availableActors = rServeRouter.map(_.toSet &~ sessionMap.values.toSet)

      log.info(s"\nNo bound actors for session $uuid found - picking available actor from $availableActors\n")

      availableActors match {

        case Some(set) if !set.isEmpty => {

          val unusedActor = set.head
          log.info(s"\n\nFound unused actor for session $uuid - it is $unusedActor\n\n")
          sessionMap += (uuid -> unusedActor)
          setTimeoutTimerForUUID(uuid, timeoutMillis)
          Some(unusedActor)
        }

        case _ => {
          log.info(s"No available actor found for session $uuid")
          None
        }
      }
    }
  }

  /*
   Time out in case the session hasn't been unbound by now
   (including if Alpine crashed but the Akka R server is running)
   */
  private def setTimeoutTimerForUUID(uuid: String, timeoutMillis: Long): Unit = {
    val tt = new TimerTask {
      override def run(): Unit = {
        sessionMap.get(uuid).map(_ ! FinishRSession(uuid))
        // wait for unbinding
        Thread.sleep(5000)
        sessionMap.remove(uuid)
      }
    }
    new Timer(uuid).schedule(tt, timeoutMillis)
  }

  /* The main message-processing method of the actor */
  def receive: Receive = {

    case x @ IsRActorAvailable(uuid) => {

      resolveActor(uuid) match {
        case Some(ref) => {
          log.info(s"\n\nIsRActorAvailable: yes\n\n")
          sender ! AvailableRActorFound
        }
        case None => {
          log.info(s"\n\nIsRActorAvailable: no\n\n")
          sender ! RActorIsNotAvailable
        }
      }

    }

    case x @ RRequest(uuid, rScript, returnSet) => {

      log.info(s"\n\nRServeMaster: received RRequest request and routing it to RServe actor\n\n")

      resolveActor(uuid) match {

        case Some(ref) => ref.tell(x, sender)
        case None => {
          log.info(s"\n\nRRequest: R actor is not available\n\n")
          sender ! RActorIsNotAvailable
        }
      }
    }

    case x @ RAssign(uuid, dataFrames) => {

      log.info(s"\n\nRServeMaster: received RAssign request and routing it to RServe actor\n\n")

      resolveActor(uuid) match {

        case Some(ref) => ref.tell(x, sender)
        case None => {
          log.info(s"\n\nRAssign: R actor is not available\n\n")
          sender ! RActorIsNotAvailable
        }
      }
    }

    /* Restart the router after it was previously stopped via RStop */
    case RStart => {

      log.info(s"\n\nMaster: Starting router\n\n")

      if (rServeRouter == None) {
        rServeRouter = createRServeRouter()
        log.info(s"\n\nRStart: created router\n\n")
      } else {
        log.info(s"\n\nR actor router was already started, ignoring request to start\n\n")
      }
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

    case x @ StartTx(sessionUuid, datasetUuid, columnNammes) => {

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

    case x @ DelimitedPacket(sessionUuid, datasetUuid, packetUuid, payload, delimiter) => {
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
      log.info(s"\nAvailable actors before unbinding: $availableActors\n\n")

      sessionMap.get(uuid).map(_.tell(x, sender))
      sessionMap.remove(uuid)
      log.info(s"\n\nR session $uuid unbound from R actor router\n")
      log.info(s"\nAvailable actors after unbinding: $availableActors\n\n")
    }

    /* Get maximum number of R workers (to set the client's blocking queue size */
    case GetMaxRWorkerCount => {

      log.info(s"\n\nGetMaxRWorkerCount: ${numRoutees}\n\n")
      sender ! MaxRWorkerCount(numRoutees)
    }

    /* In case the client wants to know how many workers are free, e.g. for resource monitoring */
    case GetFreeRWorkerCount => {
      log.info(s"\n\nGetFreeRWorkerCount: ${numRoutees - sessionMap.keySet.size}\n\n")
      sender ! FreeRWorkerCount(numRoutees - sessionMap.keySet.size)
    }
  }

}
