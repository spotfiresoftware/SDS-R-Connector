package com.alpine.rconnector.client

import akka.actor.{ Actor, OneForOneStrategy }
import akka.actor.SupervisorStrategy.{ Escalate, Restart, Resume, Stop }
import com.alpine.rconnector.messages._
import scala.concurrent.duration._
import com.alpine.rconnector.messages.RResponse
import com.alpine.rconnector.messages.RRequest
import akka.actor.OneForOneStrategy

/**
 * @author Marek Kolodziej
 * @since 5/7/14
 */
// TODO: complete Javadoc
// TODO: make it use the configuration instead of getting the path in the constructur
class Client(val url: String) extends Actor {

  // TODO: change this to config parameter rather than a constructor URL parameter
  private[this] val remoteMaster = context.actorSelection(url)

  // TODO: move this someplace else and choose the right strategy
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    //  case _: ArithmeticException => Resume
    //  case _: NullPointerException => Restart
    //  case _: IllegalArgumentException => Stop
    case _ => Escalate
  }

  def receive = {

    case r @ RStop => remoteMaster ! r
    case r @ RStart => remoteMaster ! r
    case r: RRequest => remoteMaster ! r
    case RResponse(msg) => println(s"\nClient received result from R: $msg\n")
    case RException(msg) => println(s"\nClient got exception:\n${msg}\n")
    case StartAck => println("\n\nClient: R workers started\n\n")
    case StopAck => println("\n\nClient: R workers stopped\n\n")
  }

}