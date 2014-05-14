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

import akka.kernel.Bootable
import akka.actor.{ Props, ActorSystem }
import com.typesafe.config.ConfigFactory

/**
 * This class is used by the Akka microkernel to boot up the R server
 * (the actor system accepting JVM requests to a pool of R workers).
 * <br>
 * Usage: put the jar file containing this class into Akka's bin/ directory,
 * the run
 * $bin/akka com.alpine.rconnector.server.RMicrokernelMaster
 * <br>
 * For more information, see
 * <a href="http://doc.akka.io/docs/akka/2.3.0/scala/microkernel.html">Akka's microkernel documentation</a>.
 */
class RMicrokernelMaster extends Bootable {

  val config = ConfigFactory.load()
  val system = ActorSystem.create("rServeActorSystem", config.getConfig("rServeKernelApp"))

  override def startup(): Unit = system.actorOf(Props[RServeMaster], "master")

  override def shutdown(): Unit = system.shutdown()
}