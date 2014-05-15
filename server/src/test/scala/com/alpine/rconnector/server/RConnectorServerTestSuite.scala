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

class RConnectorServerTestSuite extends FunSpec with Matchers {

  // TODO: better description
  describe("Rserve") {

    // TODO: break up into many tests
    it("should foo bar") {

      // TODO: use this as a fixture
      val rConn = mock[RConnection]

      // TODO: pull out of here, will need it in fixture for the Testkit
      class MockRServeActor extends RServeActor {
        override protected val conn = rConn
      }

      // TODO: use the same for rehearsing and testing because who knows if the creator implemented equals()
      val mean = "mean(1:10)"
      val meanResult = new REXPDouble(5.5)
      val clearWorkspace = "rm(list = ls())"
      val rExpNull = new REXPNull
      val badRCode = "thisIsBadRCode"

      // TODO: for the REXP asString, will need to mock up the REXP as well

      // TODO: rehearse cases
      when { rConn isConnected } thenReturn true
      when { rConn eval mean } thenReturn meanResult
      when { rConn eval clearWorkspace } thenReturn rExpNull
      when(rConn eval badRCode) thenThrow classOf[RserveException]

      // TODO: test cases
      rConn.isConnected should be(true)
      rConn.eval(mean) should be(meanResult)
      rConn.eval(clearWorkspace) should be(rExpNull)
      intercept[RserveException] { rConn eval badRCode }
    }
  }
}

