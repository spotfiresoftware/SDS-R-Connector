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
import com.alpine.rconnector.messages._
import com.alpine.rconnector.server.MocksAndFixtures._
import org.rosuda.REngine.Rserve.RserveException
import org.scalatest.{ BeforeAndAfterAll, FunSpec, Matchers }
import scala.collection.JavaConversions._

class RConnectorServerTestSuite extends FunSpec with Matchers with BeforeAndAfterAll {

  describe("Rserve Mock") {

    it("should replay recorded behavior") {

      import MocksAndFixtures.{ badRCode, clearWorkspace, fooResult, mean, meanResult, rConn, rExpNull }

      rConn isConnected () should be(true)
      rConn eval mean should be(meanResult)
      rConn eval clearWorkspace should be(rExpNull)
      rConn eval "foo" should be(fooResult)
      intercept[RserveException] { rConn eval badRCode }
    }
  }

  describe("RServeActor") {

    import MocksAndFixtures.{ arrX, badRCode, mean, meanResultMsg, mockRServeActor, rExpNull, RichMessage, uuid }

    implicit val ref = mockRServeActor

    it("should correctly calculate the mean of 1:10") {

      // (clientUUID: String, rScript: String, returnSet: Array[_])
      RRequest(uuid, mean, arrX).get should be(meanResultMsg)
    }

    it("should send an RException message when R evaluates code with a syntax error") {

      RRequest(uuid, badRCode, Array("badRCode")).get.getClass should be(classOf[RException])
    }

    it("should be able to clear the R workspace") {

      // the original clearWorkspace is protected, so we use the public delegating method from the mock
      mockRServeActor.underlyingActor.clrWorkspace() should be(rExpNull)
    }

    it("should be able to assign variables to the R workspace") {

      val foo = "foo"
      val vars = Array[String](foo)

      val assignAck = AssignAck(uuid, vars)

      RAssign(uuid, Map(foo -> foo)).get
        .asInstanceOf[AssignAck].variables.toList should be(assignAck.variables.toList)
    }

    it("should be able to retrieve assigned variables from the R workspace") {

      import MocksAndFixtures.{ fooExp, fooResultMsg }

      RRequest(uuid, fooExp, Array("foo")).get should be(fooResultMsg)
    }

    it("should be able to confirm finished R session") {

      FinishRSession(uuid).get should be(RSessionFinishedAck(uuid))
    }

  }

  describe("RServeActorSupervisor") {

    import MocksAndFixtures.{ arrX, badRCode, mean, meanResultMsg, RichMessage, supervisor, uuid }

    implicit val ref = supervisor

    /* If you throw an exception, RServeActor should be able to restart,
         given the MockRServeActorSupervisor's SupervisorStrategy */
    it("""should handle RServeActor's failures""") {

      // test the mean evaluation to make sure the RServeActor is running
      RRequest(uuid, mean, arrX).get should be(meanResultMsg)

      // throw an exception
      RRequest(uuid, badRCode, Array("badRCode")).get.getClass should be(classOf[RException])

      // test evaluation of correct code again
      RRequest(uuid, mean, arrX).get should be(meanResultMsg)
    }

  }

  describe("RServeMaster") {

    import MocksAndFixtures.{ arrX, mean, meanResultMsg, RichMessage, rServeMaster, uuid }

    implicit val ref = rServeMaster

    it("""should route requests to RServeActor via the
         router and RServeActorSuperviso and get back messages""") {

      RRequest(uuid, mean, arrX).get should be(meanResultMsg)

    }

    it("should be able to shut down the routees") {

      RStop.get should be(StopAck)
    }

    it("should be able to restart the routees") {

      RStart.get should be(StartAck)
    }

    it("should be able to send messages to RServeActor after router restart ") {

      RRequest(uuid, mean, arrX).get should be(meanResultMsg)
    }

    //  it("should be able to get exception messages from the R worker") {
    //    RRequest(uuid, badRCode, Array("badRCode")).get.getClass should be(classOf[RException])
    //  }

    it("should be able to reject requests when all R workers are busy") {

      import MocksAndFixtures.fooExp
      val newUUID = "555-555-1234"
      RRequest(newUUID, fooExp, Array("foo")).get should be(RActorIsNotAvailable)
    }

  }

}

