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

import akka.actor.{ ActorSystem, ActorRef }
import akka.pattern.ask
import akka.testkit.TestActorRef
import akka.util.Timeout
import com.alpine.rconnector.messages.{ AssignAck, Message, RResponse }
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito.when
import org.rosuda.REngine.{ REXPNull, REXPDouble, REXPString }
import org.rosuda.REngine.Rserve.{ RConnection, RserveException }
import org.scalatest.mock.MockitoSugar.mock
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.collection.JavaConversions._

object MocksAndFixtures {

  val uuid = "123-456-789"
  //val assignAck = AssignAck(uuid)

  implicit val system = ActorSystem("TestActorSystem", ConfigFactory.load())

  val mean = "x = mean(1:10)"
  val x = "x"
  val arrX = Array(x)
  val fooExp = "rm(list = ls()); foo = 'foo'"
  val meanResult = new REXPDouble(5.5)
  val fooResult = new REXPString("foo")
  val fooResultMsg = RResponse(Map("foo" -> fooResult.asNativeJavaObject()))
  val meanResultMsg = RResponse(Map("x" -> meanResult.asNativeJavaObject()))
  val clearWorkspace = "rm(list = ls())"
  val rExpNull = new REXPNull
  val badRCode = "thisIsBadRCode"

  // mock the R connection
  val rConn = mock[RConnection]
  when { rConn isConnected } thenReturn true
  when { rConn eval mean } thenReturn meanResult
  when { rConn eval clearWorkspace } thenReturn rExpNull
  /* NOTE: The Rserve mock throws an RserveException _exception_,
     but the RServeActor sends an RException _message_ to the calling actor
     upon the RserveException being thrown by R and propagated to Rserve */
  when(rConn eval badRCode) thenThrow classOf[RserveException]
  when(rConn eval x) thenReturn (meanResult)
  when(rConn eval "foo") thenReturn (fooResult)

  // have RServeActor use the mock of the R connection
  class MockRServeActor extends RServeActor {

    override protected val conn = rConn
    def clrWorkspace() = clearWorkspace()
  }

  val mockRServeActor = TestActorRef(new MockRServeActor())

  /* have RServeActorSupervisor use the mock of the R connection
     (via MockRServeActor) */
  class MockRServeActorSupervisor extends RServeActorSupervisor {

    override protected val rServe = TestActorRef(new MockRServeActor())
  }

  val supervisor = TestActorRef(new MockRServeActorSupervisor())

  /* have RServeMaster use the mock of the R connection
     (via MockRServeActorSupervisor and MockRServeActor */
  class MockRServeMaster extends RServeMaster {

    // routers don't work in the unit test context, so create one supervisor
    override protected val numRoutees = 1

    override protected def createRServeRouter(): Option[Vector[ActorRef]] =
      Some(Vector(TestActorRef(new MockRServeActorSupervisor())))

    rServeRouter = createRServeRouter()

    println(s"\n\nnumActors = $numRoutees\n\n")

  }

  implicit val rServeMaster = TestActorRef(new MockRServeMaster())

  val duration = 30 seconds
  implicit val timeout = Timeout(duration)

  implicit class RichMessage(x: Message) {

    def get(implicit ref: ActorRef): Any = Await.result(ref ? x, duration)
  }

}
