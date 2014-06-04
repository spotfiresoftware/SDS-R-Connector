/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alpine.rconnector.messages

/* Messages for communication between Alpine's backend and the R server.
 * <p>
 * <b>Note:</b> even though the messages are serializable using the Java serialization API,
 * Akka uses Protobuf by default, and that is preferred. The Serializable market trait
 * was only included to make the configuration more flexible.
 */
sealed trait Message extends Serializable

/**
 * Request from Scala/Java to R
 * @param rScript message - R code to execute
 * @param returnSet - set of elements to return after R execution
 * (a Java HashSet as opposed to a Scala one becasuse the object will be instantiated in Java)
 */
case class RRequest(clientUUID: String, rScript: String,
  returnSet: java.util.List[String]) extends Message

/**
 *
 * @param dataFrames
 */
case class RAssign(uuid: String, dataFrames: java.util.Map[String, String]) extends Message

/**
 * Response from R to Scala/Java
 * @param map - map of results coming back from R to Scala/Java
 */
case class RResponse(map: java.util.Map[String, Any]) extends Message

/**
 *
 * R exception message
 */
case class RException(message: String) extends Message

/**
 * Request that the Akka server start the R runtimes.
 * The Akka server itself will keep on running.
 */
case object RStart extends Message

/**
 * Acknowledge R worker start
 */
case object StartAck extends Message

/**
 * Request that the Akka server shut down the R runtimes.
 * The Akka server itself will keep on running.
 */
case object RStop extends Message

/**
 * Acknowledge R worker stop
 */
case object StopAck extends Message

/**
 *
 * @param uuid
 */
case class FinishRSession(uuid: String)

/**
 *
 * @param uuid
 */
case class AssignAck(uuid: String)