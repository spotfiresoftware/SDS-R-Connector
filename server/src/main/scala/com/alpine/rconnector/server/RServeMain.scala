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

import akka.actor.{ Props, ActorSystem }
import akka.event.Logging
import com.typesafe.config.ConfigFactory

/**
 * This starts up the R server actor system.
 */
object RServeMain {

  val config = ConfigFactory.load()
  private val system = ActorSystem.create("rServeActorSystem", config.getConfig("rServeKernelApp"))

  def startup(): Unit = system.actorOf(Props[RServeMaster], "master")
  def shutdown(): Unit = system.shutdown()

  // need to exit cleanly (e.g. on Ctrl+C), so shut down actor system and free up ports
  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run: Unit = {
      println("Shutting down actor system")
      shutdown()
      println("Shutdown complete")
    }
  })

  def main(args: Array[String]): Unit = startup()

}