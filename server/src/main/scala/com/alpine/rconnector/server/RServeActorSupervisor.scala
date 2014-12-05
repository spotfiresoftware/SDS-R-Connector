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

import akka.actor.{ ActorKilledException, Props, Actor, Kill, OneForOneStrategy }
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.strands.Strand
import com.alpine.rconnector.messages._
import com.jezhumble.javasysmon.{ JavaSysMon, OsProcess }
import org.rosuda.REngine.Rserve.RserveException
import org.rosuda.REngine.{ REXPMismatchException, REngineEvalException, REngineException }
import akka.actor.SupervisorStrategy.{ Escalate, Restart }
import scala.concurrent.duration._
import akka.event.Logging
import akka.actor.ActorKilledException
import scala.sys.process._

/**
 * This actor supervises individual RServeActors. Without supervision, each RServeActor
 * would have to have its own try/catch blocks, and without handling at this level,
 * the exceptions would percolate up to the router, causing all routees to be restarted.
 */
class RServeActorSupervisor extends Actor {

  private implicit val log = Logging(context.system, this)
  protected[this] val rServe = context.actorOf(Props[RServeActor])
  private var pid: Int = _
  // TODO: use or drop
  //  private var memUsage: MemUsage = _

  logActorStart(this)

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 60, withinTimeRange = 1 minute) {

    /* Capture the known exceptions, log the failure and restart actor.
       The actor being restarted will tell the sender about the failure. */
    case e @ (_: RserveException | _: REngineException |
      _: REngineEvalException | _: REXPMismatchException |
      _: RuntimeException
      ) => {

      e.printStackTrace()
      logFailure(e)
      Restart
    }

    case _ => Escalate
  }

  def receive = {

    case PId(pid) => {
      log.info(s"Supervisor: received PId($pid)")
      //      memUsage = new MemUsage(1024 * 1024 * 100L, pid)
      this.pid = pid
      // TODO: enable memory monitoring (see the MemUsage example below)
    }

    case FinishRSession(uuid) => {
      log.info(s"Received request to finish R session. Killing R process")
      killRProcess()
      log.info(s"Killing child and restarting")
      rServe ! Kill
      // TODO: use or drop
      //      memUsage = new MemUsage(1024 * 1024 * 16, pid)
    }

    /* This is just the RException sent due to the actor being killed by the supervisor.
       We can ignore it. We can add some extra logic here later*/
    case RException(t) => {
    }

    case msg: Any => rServe.tell(msg, sender)
  }

  private def killRProcess(): Int = {

    if (System.getProperty("os.name").toLowerCase().contains("windows")) {

      // TODO: this could be managed by new JavaSysMon().kilProcess(pid) instead
      s"taskkill /PID $pid /F" !

    } else {

      // TODO: this could be managed by new JavaSysMon().kilProcess(pid) instead
      s"kill -9 $pid" !
    }
  }

  // TODO: This isn't yet hooked up to anything
  // TODO: Base maxMem on R server settings
  // http://jezhumble.github.io/javasysmon/
  //  private class MemUsage(maxMem: Long, pid: Int) {
  //    var state = true
  //    val fiber: Strand = new Fiber[Unit]() {
  //      override protected def run(): Unit = {
  //        val monitor = new JavaSysMon()
  //        val process = monitor.processTree().find(pid)
  //        while (state) {
  //          val info = process.processInfo
  //          val resident = info.getResidentBytes
  //          if (resident >= maxMem) {
  //            // TODO: send a message to the client that R exceeded the allowed memory
  //            // TODO: kill the R process and the worker actor via FinishRSession(uuid)
  //            monitor.killProcess(pid)
  //            rServe ! Kill
  //            state = false
  //          }
  //          Fiber.sleep(100L)
  //        }
  //      }
  //      // TODO: need to call cancel, or else this fiber will keep running forever, causing a leak
  //      def cancel(): Unit = {
  //        state = false
  //      }
  //    }
  //  }

}
