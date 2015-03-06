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

import java.util.{ List => JList, Map => JMap }

/**
 * Messages for communication between Alpine's backend and the R server.
 * <p>
 * <b>Note:</b> even though the messages are serializable using the Java serialization API,
 * Akka uses Protobuf by default, and that is preferred. The Serializable market trait
 * was only included to make the configuration more flexible.
 */
sealed trait Message extends Serializable

/**
 *
 * @param rConsoleOutput
 * @param outputDataFrame
 */
case class ReturnNames(rConsoleOutput: String, outputDataFrame: Option[String] = Some("alpine_output"))

/**
 *
 * @param uuid
 * @param rScript
 * @param returnNames
 * @param numPreviewRows
 * @param escapeStr
 * @param inputDelimiterStr
 * @param delimiterStr
 * @param quoteStr
 * @param httpUploadUrl
 * @param httpUploadHeader
 */
sealed class RRequest(
  val uuid: String,
  val rScript: String,
  val returnNames: Option[ReturnNames] = None,
  val numPreviewRows: Long = 1000,
  val escapeStr: Option[String] = None,
  val inputDelimiterStr: Option[String] = None,
  val outputDelimiterStr: Option[String] = None,
  val quoteStr: Option[String] = None,
  val httpUploadUrl: Option[String] = None,
  val httpUploadHeader: Option[JMap[String, String]] = None) extends Message

object RRequest {

  def unapply(req: RRequest) =
    Some(req.uuid, req.rScript, req.returnNames, req.numPreviewRows, req.escapeStr, req.inputDelimiterStr,
      req.outputDelimiterStr, req.quoteStr, req.httpUploadUrl, req.httpUploadHeader)
}

/**
 *
 * @param uuid
 * @param rScript
 */
case class SyntaxCheckRequest(
  override val uuid: String,
  override val rScript: String)
    extends RRequest(uuid, rScript)

/**
 *
 * @param uuid
 * @param rScript
 * @param returnNames
 * @param numPreviewRows
 * @param escapeStr
 * @param inputDelimiterStr
 * @param outputDelimiterStr
 * @param quoteStr
 * @param httpUploadUrl
 * @param httpUploadHeader
 */
class ExecuteRRequest(
  override val uuid: String,
  override val rScript: String,
  override val returnNames: Some[ReturnNames],
  override val numPreviewRows: Long = 1000,
  override val escapeStr: Option[String] = None,
  override val inputDelimiterStr: Option[String] = None,
  override val outputDelimiterStr: Option[String] = None,
  override val quoteStr: Option[String] = None,
  override val httpUploadUrl: Option[String] = None,
  override val httpUploadHeader: Option[JMap[String, String]] = None)
    extends RRequest(uuid, rScript, returnNames, numPreviewRows)

object ExecuteRRequest {

  def unapply(req: ExecuteRRequest) =
    Some(req.uuid, req.rScript, req.returnNames, req.numPreviewRows,
      req.escapeStr, req.inputDelimiterStr, req.outputDelimiterStr, req.quoteStr,
      req.httpUploadUrl, req.httpUploadHeader
    )
}

/**
 *
 * @param uuid
 * @param rScript
 * @param returnNames
 * @param numPreviewRows
 * @param escapeStr
 * @param inputDelimiterStr
 * @param outputDelimiterStr
 * @param quoteStr
 * @param httpUploadUrl
 * @param httpUploadHeader
 * @param columnNames
 */
case class HadoopExecuteRRequest(
  override val uuid: String,
  override val rScript: String,
  override val returnNames: Some[ReturnNames],
  override val numPreviewRows: Long = 1000,
  override val escapeStr: Option[String] = None,
  override val inputDelimiterStr: Option[String] = None,
  override val outputDelimiterStr: Option[String] = None,
  override val quoteStr: Option[String] = None,
  override val httpUploadUrl: Option[String] = None,
  override val httpUploadHeader: Option[JMap[String, String]] = None,
  val columnNames: Option[JList[String]] = None)
    extends ExecuteRRequest(
      uuid, rScript, returnNames, numPreviewRows, escapeStr, inputDelimiterStr,
      outputDelimiterStr, quoteStr, httpUploadUrl, httpUploadHeader
    )

/**
 *
 * @param uuid
 * @param rScript
 * @param returnNames
 * @param numPreviewRows
 * @param escapeStr
 * @param inputDelimiterStr
 * @param outputDelimiterStr
 * @param quoteStr
 * @param httpUploadUrl
 * @param httpUploadHeader
 * @param schemaName
 * @param tableName
 */
case class DBExecuteRRequest(
  override val uuid: String,
  override val rScript: String,
  override val returnNames: Some[ReturnNames],
  override val numPreviewRows: Long = 1000,
  override val escapeStr: Option[String] = None,
  override val inputDelimiterStr: Option[String] = None,
  override val outputDelimiterStr: Option[String] = None,
  override val quoteStr: Option[String] = None,
  override val httpUploadUrl: Option[String] = None,
  override val httpUploadHeader: Option[JMap[String, String]] = None,
  val schemaName: Option[String],
  val tableName: Option[String])
    extends ExecuteRRequest(
      uuid, rScript, returnNames, numPreviewRows, escapeStr, inputDelimiterStr,
      outputDelimiterStr, quoteStr, httpUploadUrl, httpUploadHeader
    )

/**
 *
 * @param uuid
 * @param objects
 * @param httpDownloadUrl
 * @param httpDownloadHeader
 */
case class RAssign(
  val uuid: String,
  val objects: JMap[String, Any],
  val httpDownloadUrl: Option[String] = None,
  val httpDownloadHeader: Option[JMap[String, String]] = None) extends Message

/**
 *
 * @param consoleOutput
 * @param previewDataFrame
 */
case class RResponse(consoleOutput: Array[String], previewDataFrame: Option[JMap[String, Object]]) extends Message

/**
 *
 * R exception message
 */
case class RException(exception: Throwable) extends Message

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
case class FinishRSession(uuid: String) extends Message

/**
 *
 * @param uuid
 */
case class AssignAck(uuid: String, variables: Array[String]) extends Message

/**
 *
 * @param uuid
 */
case class IsRActorAvailable(uuid: String) extends Message

/**
 *
 */
case object AvailableRActorFound extends Message

/**
 *
 */
case object RActorIsNotAvailable extends Message

/**
 *
 * @param uuid
 */
case class RSessionFinishedAck(uuid: String) extends Message

/**
 *
 */
case object GetMaxRWorkerCount extends Message

/**
 *
 * @param i
 */
case class MaxRWorkerCount(i: Int) extends Message

/**
 *
 */
case object GetFreeRWorkerCount extends Message

/**
 *
 * @param i
 */
case class FreeRWorkerCount(i: Int) extends Message

/**
 *
 */
case object RemoteFrameSizeRequest extends Message

/**
 *
 * @param sizeBytes
 */
case class RemoteFrameSizeResponse(sizeBytes: Long) extends Message

/**
 *
 */
case object RegisterRemoteActor extends Message

/**
 *
 */
case object RemoteActorRegistered extends Message

/**
 *
 * @param pid
 */
case class PId(pid: Int) extends Message

/**
 *
 * @param uuid
 */
case class SyntaxCheckOK(uuid: String) extends Message

/**
 *
 * @param uuid
 */
case class StopActor(uuid: String) extends Message