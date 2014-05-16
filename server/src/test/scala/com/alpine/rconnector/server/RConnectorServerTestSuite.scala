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

import akka.testkit.TestActorRef
import org.rosuda.REngine.Rserve.{ RConnection, RserveException }
import org.mockito.Mockito.{ when }
import org.rosuda.REngine.{ REXPNull, REXPDouble }
import org.scalatest.FunSpec
import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar.mock
import com.alpine.rconnector.messages.{ RRequest, RResponse, RException }
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await

class RConnectorServerTestSuite extends FunSpec with Matchers with BeforeAndAfterAll {

  implicit val system = ActorSystem("TestActorSystem", ConfigFactory.load())

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val timeout = Timeout(30 seconds)

  object RMockFixture {

    val mean = "mean(1:10)"
    val meanResult = new REXPDouble(5.5)
    val clearWorkspace = "rm(list = ls())"
    val rExpNull = new REXPNull
    val badRCode = "thisIsBadRCode"

    val rConn = mock[RConnection]
    when { rConn isConnected } thenReturn true
    when { rConn eval mean } thenReturn meanResult
    when { rConn eval clearWorkspace } thenReturn rExpNull
    /* NOTE: The Rserve mock throws an RserveException _exception_,
       but the RServeActor sends an RException _message_ to the calling actor
       upon the RserveException being thrown by R and propagated to Rserve */
    when(rConn eval badRCode) thenThrow classOf[RserveException]
  }

  class MockRServeActor extends RServeActor {

    override protected val conn = RMockFixture.rConn
  }

  describe("Rserve Mock") {

    it("should replay recorded behavior") {

      import RMockFixture._

      rConn isConnected () should be(true)
      rConn eval mean should be(meanResult)
      rConn eval clearWorkspace should be(rExpNull)
      intercept[RserveException] { rConn eval badRCode }

    }
  }

  describe("RServeActor") {

    val mockRServeActor = TestActorRef(new MockRServeActor())

    import RMockFixture.{ badRCode, mean }

    implicit class RichAny(x: Any) {
      def get: Any = Await.result(mockRServeActor ? x, Duration.Inf)
    }

    it("should correctly calculate the mean of 1:10") {

      RRequest(mean).get should be(RResponse("5.5"))
    }

    it("should send an RException message when R evaluates code with a syntax error") {

      RRequest(badRCode).get.getClass should be(classOf[RException])
    }

  }

}

