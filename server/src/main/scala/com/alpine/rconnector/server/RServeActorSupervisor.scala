package com.alpine.rconnector.server

import akka.actor.{ Props, Actor, OneForOneStrategy }
import org.rosuda.REngine.Rserve.RserveException
import org.rosuda.REngine.{ REXPMismatchException, REngineEvalException, REngineException }
import akka.actor.SupervisorStrategy.{ Escalate, Restart }
import scala.concurrent.duration._
import akka.event.Logging

/**
 * Created by marek on 5/13/14.
 */
class RServeActorSupervisor extends Actor {

  implicit private[this] val log = Logging(context.system, this)
  val rServe = context.actorOf(Props[RServeActor])

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
