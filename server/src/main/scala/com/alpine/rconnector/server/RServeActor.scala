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

import java.io.{ FileInputStream, FileOutputStream, File }

import org.apache.http.HttpVersion
import org.apache.http.client.methods.{ HttpPost, HttpGet }
import org.apache.http.entity.{ ContentType, FileEntity }
import org.rosuda.REngine.Rserve.RConnection
import akka.AkkaException
import akka.actor.Actor
import com.alpine.rconnector.messages._
import akka.event.Logging
import org.rosuda.REngine.REXP
import org.apache.http.impl.client.HttpClientBuilder
import scala.collection.JavaConversions._
import com.alpine.rconnector.messages._
import java.util.{ Map => JMap, UUID }
import scala.collection.mutable.{ HashMap => MutableHashMap }
import scala.sys.process._
import resource._
import org.apache.http.HttpStatus
import scala.collection.mutable

/**
 * This is the actor that establishes a connection to R via Rserve
 * (see <a href="http://rforge.net/Rserve/">Rserve documentation</a>).
 * <br>
 * TCP connections to R are kept for as long as the actor is alive.
 * If the actor is killed by its supervisor or throws an exception, the connection
 * gets released.
 */
class RServeActor extends Actor {

  private[this] implicit val log = Logging(context.system, this)

  protected[this] var conn: RConnection = _
  protected[this] var pid: Int = _
  protected[this] var tempFilePath: String = _

  val downloadExtension = "download"
  val uploadExtension = "upload"

  def updateConnAndPid() = {

    conn = new RConnection()
    pid = conn.eval("Sys.getpid()").asNativeJavaObject.asInstanceOf[Array[Int]](0)
    log.info(s"New R PID is $pid")
    context.parent ! PId(pid)
  }

  override def preStart(): Unit = updateConnAndPid()

  logActorStart(this)

  private def killRProcess(): Int = {
    log.info(s"Killing R process")
    s"kill -9 $pid" !
  }

  override def postStop(): Unit = killRProcess()

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    // send message about exception to the client (e.g. for UI reporting)
    log.info("preRestart")
    sender ! RException(reason)
    killRProcess()
    super.preRestart(reason, message)
  }

  // that's the default anyway, but you can do something different
  override def postRestart(reason: Throwable): Unit = {
    log.info("postRestart")
    preStart()
  }

  // remove all temporary data from R workspace
  protected[this] def clearWorkspace() = conn.eval("rm(list = ls())")

  private def eval(s: String): java.lang.Object = {
    val enrichedScript =

      s"""try({
                         $s
                       },
                       silent=TRUE)""".stripMargin
    log.info(s"\nEvaluating $enrichedScript\n")
    val res: REXP = conn.parseAndEval(enrichedScript) // eval
    if (res.inherits("try-error")) {
      log.info(s"Error in script:\n$s")
      throw new RuntimeException(res.asString)
    }
    res.asNativeJavaObject
  }

  def receive: Receive = {

    case RAssign(uuid, objects, httpDownloadUrl, httpDownloadHeader) => {

      if (httpDownloadUrl != None && httpDownloadHeader != None) {

        log.info("Download URL and header are present - performing REST download")
        restDownload(httpDownloadUrl, httpDownloadHeader, uuid)
      }

      log.info("Assigning objects (if any) to R workspace")
      objects.foreach { elem =>

        elem match {

          case (x, y: String) => conn.assign(x, y)
          case (x, y: Array[Byte]) => conn.assign(x, y)
          case (x, y) => throw new IllegalStateException(
            s"Unsupported type of value $x Expected either String or Array[Byte] but got ${y.getClass.getName}"
          )
        }
      }

      log.info("Acking assignment to client")
      sender ! AssignAck(uuid, objects.keySet.map(_.toString).toArray)
    }

    case SyntaxCheckRequest(uuid, rScript) => {

      log.info("Evaluating syntax check request")
      eval(rScript)

      // We won't get here if an exception occurred
      sender ! SyntaxCheckOK(uuid)
    }

    case ExecuteRRequest(uuid, rScript, Some(returnNames), numPreviewRows, escapeStr,
      Some(delimiterStr), quoteStr, httpUploadUrl, httpUploadHeader

      ) => {

      log.info("Received ExecuteRRequest. Evaluating enriched script")
      // execute R script
      eval(enrichRScript(rScript, returnNames.rConsoleOutput, downloadLocalPath(uuid), uploadLocalPath(uuid), delimiterStr, numPreviewRows))

      val rConsoleOutput = eval(returnNames.rConsoleOutput)
      val dataPreview = eval(returnNames.outputDataFrame)

      if (httpUploadUrl != None && httpUploadHeader != None) {

        log.info(s"Upload URL and header are present - performing REST upload")

        restUpload(rScript, httpUploadUrl.get, httpUploadHeader.get, uuid)
      }

      log.info(s"Deleting temp files")
      deleteTempFiles(rScript, uuid)

      log.info(s"Sending R response to client")
      sender ! RResponse(rConsoleOutput.asInstanceOf[String], dataPreview.asInstanceOf[Map[String, Object]])

    }

    case FinishRSession(uuid) => {

      log.info(s"Finishing R session for UUID $uuid")
      killRProcess()
      updateConnAndPid()
      log.info(s"Sending RSessionFinishedAck")
      sender ! RSessionFinishedAck(uuid)
    }

    case other => {

      val errMsg = s"Unexpected message of type ${other.getClass.getName} from $sender"
      log.error(errMsg)
      throw new AkkaException(errMsg)
    }

  }

  // client will pass in the header info, depending on whether it's DB or Hadoop
  // mutable map is necessary due to implicit conversion from java.util.Map
  private def restDownload(url: Option[String], header: Option[JMap[String, String]], uuid: String): Unit = {

    if (url != None && header != None) {

      val localPath = downloadLocalPath(uuid)

      log.info(
        s"""Starting download from $url
        with header $header
        into local file $localPath
        """.stripMargin)

      // try-with-resources (thanks to Josh Suereth's scala-arm library)
      for {

        client <- managed(HttpClientBuilder.create().build())
        fos <- managed(new FileOutputStream(new File(localPath)))

      } {

        val get = new HttpGet(url.get) {

          setProtocolVersion(HttpVersion.HTTP_1_1) // ensure chunking
          header.get.foreach { case (k, v) => setHeader(k, v) }
        }

        val response = client.execute(get)
        response.getEntity.writeTo(fos)

        val statusLine = response.getStatusLine
        val statusCode = statusLine.getStatusCode
        get.releaseConnection()

        if (statusCode != HttpStatus.SC_OK) {

          throw new RuntimeException(s"REST download of R dataset from Alpine to R server failed with status code $statusCode. Message: ${statusLine.getReasonPhrase}")
        }

        log.info(s"File $localPath downloaded successfully")
      }

    }

  }

  // mutable map is necessary due to implicit conversion from java.util.Map
  private def restUpload(rScript: String, url: String, header: mutable.Map[String, String], uuid: String): Unit = {

    log.info("In restUpload")
    if (hasOutput(rScript)) {

      // TODO: change localPath to config param?
      val localPath = uploadLocalPath(uuid)

      log.info(s"Local upload path: $localPath")

      for { client <- managed(HttpClientBuilder.create().build()) } {

        val post = new HttpPost(url) {

          setProtocolVersion(HttpVersion.HTTP_1_1) // ensure chunking
          setEntity(new FileEntity(new File(localPath), ContentType.MULTIPART_FORM_DATA))
          header.foreach { case (k, v) => setHeader(k, v) }
        }

        val response = client.execute(post)
        val statusLine = response.getStatusLine
        val statusCode = statusLine.getStatusCode
        post.releaseConnection()

        if (statusCode != HttpStatus.SC_OK) {

          throw new RuntimeException(s"REST upload from R server to Alpine failed with status code $statusCode. Message: ${statusLine.getReasonPhrase}")

        }

        log.info(s"File $localPath uploaded successfully")
      }

    }
  }

  private def deleteTempFile(localPath: String): Unit = new File(localPath).delete()

  private def deleteDownloadTempFile(rScript: String, uuid: String): Unit =
    if (hasInput(rScript)) deleteTempFile(downloadLocalPath(uuid))

  private def deleteUploadTempFile(rScript: String, uuid: String): Unit =
    if (hasOutput(rScript)) deleteTempFile(uploadLocalPath(uuid))

  private def deleteTempFiles(rScript: String, uuid: String): Unit = {

    deleteDownloadTempFile(rScript, uuid)
    deleteUploadTempFile(rScript, uuid)
  }

  private def enrichRScript(rawScript: String,
    consoleOutputVar: String,
    inputPath: String,
    outputPath: String,
    delimiterStr: String,
    previewNumRows: Long): String = {

    val enrichedScript = s"""
            $consoleOutputVar <- capture.output({

            library(data.table);

            ${if (hasInput(rawScript)) s"alpine_input <- fread(input=$inputPath, sep=$delimiterStr);" else ""}

            $rawScript

            ${
      if (hasOutput(rawScript))
        s"""# write temp table to disk
                  write.table(x = alpine_output, file='$outputPath', sep=$delimiterStr, append=FALSE, quote=TRUE, row.names=FALSE)
                  # preview this many rows in UI
                  alpine_output <- alpine_output[1:$previewNumRows, ]
                  """
      else ""
    }
            });
        """.stripMargin

    log.info(s"Enriched script:\n$enrichedScript")
    enrichedScript
  }

  private def downloadLocalPath(uuid: String) = s"${RServeMain.localTempDir}/$uuid.$downloadExtension"

  private def uploadLocalPath(uuid: String) = s"${RServeMain.localTempDir}/$uuid.$uploadExtension"

  private def hasInput(rScript: String) = Utils.containsNotInComment(rScript, "alpine_input", "#")

  private def hasOutput(rScript: String) = Utils.containsNotInComment(rScript, "alpine_output", "#")

}