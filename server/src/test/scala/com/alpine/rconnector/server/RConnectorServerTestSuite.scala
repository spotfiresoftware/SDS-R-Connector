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

import akka.actor.ActorRef
import akka.testkit.TestKit
import com.alpine.rconnector.messages.{ RRequest, RException, RStart, RStop, StartAck, StopAck }
import org.rosuda.REngine.Rserve.RserveException
import org.scalatest.{ BeforeAndAfterAll, FunSpec, Matchers }

class RConnectorServerTestSuite extends FunSpec with Matchers with BeforeAndAfterAll {

  implicit val system = MocksAndFixtures.system
  override def afterAll = TestKit.shutdownActorSystem(system)

  describe("Rserve Mock") {

    it("should replay recorded behavior") {

      import MocksAndFixtures.{ badRCode, clearWorkspace, mean, meanResult, rConn, rExpNull }

      rConn isConnected () should be(true)
      rConn eval mean should be(meanResult)
      rConn eval clearWorkspace should be(rExpNull)
      intercept[RserveException] { rConn eval badRCode }
    }
  }

  describe("RServeActor") {

    import MocksAndFixtures.{ badRCode, mean, meanResultMsg, mockRServeActor, rExpNull, RichMessage }

    implicit val ref: ActorRef = mockRServeActor

    it("should correctly calculate the mean of 1:10") {

      RRequest(mean).get should be(meanResultMsg)
    }

    it("should send an RException message when R evaluates code with a syntax error") {

      RRequest(badRCode).get.getClass should be(classOf[RException])
    }

    it("should be able to clear the R workspace") {

      // the original clearWorkspace is protected, so we use the public delegating method from the mock
      mockRServeActor.underlyingActor.clrWorkspace() should be(rExpNull)
    }
  }

  describe("RServeActorSupervisor") {

    import MocksAndFixtures.{ badRCode, mean, meanResultMsg, RichMessage, supervisor }

    implicit val ref = supervisor

    /* If you throw an exception, RServeActor should be able to restart,
         given the MockRServeActorSupervisor's SupervisorStrategy */
    it("""should handle RServeActor's failures""") {

      // test the mean evaluation to make sure the RServeActor is running
      RRequest(mean).get should be(meanResultMsg)

      // throw an exception
      RRequest(badRCode).get.getClass should be(classOf[RException])

      // test evaluation of correct code again
      RRequest(mean).get should be(meanResultMsg)
    }
  }

  describe("RServeMaster") {

    import MocksAndFixtures.{ mean, meanResultMsg, RichMessage, rServeMaster }
    implicit val ref = rServeMaster

    it("""should route requests to RServeActor via the
         router and RServeActorSuperviso and get back messages""") {

      RRequest(mean).get should be(meanResultMsg)

    }

    it("should be able to shut down the routees") {

      RStop.get should be(StopAck)
    }

    it("should be able to restart the routees") {

      RStart.get should be(StartAck)
    }

    it("should be able to send messages to RServeActor after router restart ") {

      RStart.get
      RRequest(mean).get should be(meanResultMsg)
    }

  }
}

