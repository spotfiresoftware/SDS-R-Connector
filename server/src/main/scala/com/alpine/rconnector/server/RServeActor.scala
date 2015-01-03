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

import com.alpine.rconnector.messages._

import akka.AkkaException
import akka.actor.Actor
import akka.event.Logging
import java.io.{ StringWriter, FileInputStream, FileOutputStream, File }
import java.security.{ SecureRandom, KeyStore }
import java.util.{ List => JList, Map => JMap }
import javax.net.ssl.{ TrustManager, KeyManager, SSLContext }
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringEscapeUtils.escapeJava
import org.apache.http.{ HttpStatus, HttpVersion }
import org.apache.http.client.methods.{ HttpPost, HttpGet }
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.conn.ssl.{ SSLConnectionSocketFactory, SSLContexts, TrustSelfSignedStrategy }
import org.apache.http.entity.{ ContentType, FileEntity }
import org.apache.http.entity.mime.{ HttpMultipartMode, MultipartEntityBuilder }
import org.apache.http.entity.mime.content.{ FileBody, StringBody }
import org.apache.http.impl.client.{ CloseableHttpClient, HttpClients, HttpClientBuilder }
import org.rosuda.REngine.REXP
import org.rosuda.REngine.Rserve.RConnection
import RServeMain.autoDeleteTempFiles
import scala.collection.mutable.Map
import scala.collection.JavaConversions._
import scala.sys.process._
import scala.util.{ Failure, Try, Success }

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
  val parseBoilerplate = "parse(text = rawScript)"

  // trust self-signed SSL certificates
  val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
  val sslCtx = SSLContexts.custom().loadTrustMaterial(trustStore, new TrustSelfSignedStrategy()).build()
  val sslConnFactory = new SSLConnectionSocketFactory(sslCtx,
    Array("SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"), null,
    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)

  /* Note: ALLOW_ALL_HOSTNAME_VERIFIER doesn't check domain correctness, e.g. it will accept
     localhost/127.0.0.1 instead of alpineqa3.alpinenow.local/10.0.0.204
     Set to BROWSER_COMPATIBLE_HOSTNAME_VERIFIER and use correctly issued
     SSL certificates if this is a problem.
   */

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

      log.info(s"Received RAssign message")

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
      conn.assign("rawScript", rScript)

      try {

        eval(parseBoilerplate)

      } catch {

        case e: Exception =>
          {
            deleteTempFiles(uuid)
            val msg = e.getMessage
            val errorIn = "Error in"
            if (msg.contains(parseBoilerplate)) {
              val noBoilerplate = msg.replace(parseBoilerplate, "")
              val noErrorIn =
                if (noBoilerplate.startsWith(errorIn)) noBoilerplate.replaceFirst(errorIn, "") else noBoilerplate
              val leadingRegex = "[^a-zA-Z]+text[^a-zA-Z]+".r
              val finalStr = leadingRegex.replaceFirstIn(noErrorIn, "")
              throw new RuntimeException(finalStr)

            } else {

              throw e
            }
          }
      }

      log.info("Evaluation successful. Sending response back to Alpine.")
      // We won't get here if an exception occurred
      sender ! SyntaxCheckOK(uuid)
    }

    case HadoopExecuteRRequest(uuid, rScript, Some(returnNames), numPreviewRows, Some(escapeStr),
      Some(delimiterStr), Some(quoteStr), httpUploadUrl, httpUploadHeader, columnNames

      ) => {

      log.info("Received HadoopExecuteRRequest. Evaluating enriched script")

      var rResponse: RResponse = null

      try {

        rResponse = processRequest(uuid, rScript, returnNames, numPreviewRows,
          escapeStr, delimiterStr, quoteStr, columnNames
        )
        hadoopRestUpload(httpUploadUrl, httpUploadHeader, uuid)

      } finally {

        if (autoDeleteTempFiles) {

          deleteTempFiles(uuid)
        }

      }

      log.info(s"Sending R response to client")
      sender ! rResponse

    }

    case DBExecuteRRequest(uuid, rScript, Some(returnNames), numPreviewRows, Some(escapeStr),
      Some(delimiterStr), Some(quoteStr), httpUploadUrl, httpUploadHeader, schemaName, tableName
      ) => {

      log.info("Received HadoopExecuteRRequest. Evaluating enriched script")

      var rResponse: RResponse = null

      try {

        rResponse = processRequest(uuid, rScript, returnNames, numPreviewRows, escapeStr, delimiterStr, quoteStr, None)

        dbRestUpload(
          httpUploadUrl, httpUploadHeader, uuid, delimiterStr, quoteStr, escapeStr,
          schemaName, tableName
        )

      } finally {

        if (autoDeleteTempFiles) {

          deleteTempFiles(uuid)
        }

      }

      log.info(s"Sending R response to client")
      sender ! rResponse

    }

    case FinishRSession(uuid) => {

      log.info(s"Finishing R session for UUID $uuid")
      killRProcess()
      updateConnAndPid()
      log.info(s"Sending RSessionFinishedAck")

      if (autoDeleteTempFiles) {
        deleteTempFiles(uuid)
      }

      sender ! RSessionFinishedAck(uuid)
    }

    case other => {

      val errMsg = s"Unexpected message of type ${other.getClass.getName} from $sender"
      log.error(errMsg)
      throw new AkkaException(errMsg)
    }

  }

  /*
        // configure the SSLContext with a TrustManager

        URL url = new URL("https://mms.nw.ru");
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        });
        System.out.println(conn.getResponseCode());
        conn.disconnect();
    }
 */

  // client will pass in the header info, depending on whether it's DB or Hadoop
  // mutable map is necessary due to implicit conversion from java.util.Map
  private def restDownload(url: Option[String], header: Option[JMap[String, String]], uuid: String): Unit = {

    if (url != None && header != None) {

      val localPath = downloadLocalPath(uuid)

      log.info(
        s"""Starting download from ${url.get}
        with header ${header.get}
        into local file $localPath
        """.stripMargin)

      // TODO: refactor with try-with-resources (Josh Suereth's scala-arm library)
      var client: CloseableHttpClient = null
      var fos: FileOutputStream = null
      var get: HttpGet = null

      try {

        client = HttpClients.custom().setSSLSocketFactory(sslConnFactory).build()

        //        client = HttpClientBuilder
        //          .create() //  .setHostnameVerifier(new DefaultHostnameVerifier())
        //          .build()
        fos = new FileOutputStream(new File(localPath))

        get = new HttpGet(url.get) {

          setProtocolVersion(HttpVersion.HTTP_1_1) // ensure chunking
          header.get.foreach { case (k, v) => setHeader(k, v) }
        }

        val response = client.execute(get)

        val statusLine = response.getStatusLine
        val statusCode = statusLine.getStatusCode

        if (statusCode != HttpStatus.SC_OK) {

          val excMsg = s"REST download of R dataset from Alpine to R server failed with status code $statusCode. Message: ${statusLine.getReasonPhrase}. Check R server log for more details."
          log.error(excMsg)
          // This will report the exception in the log
          log.error(IOUtils.toString(response.getEntity.getContent, "UTF-8"))
          throw new RuntimeException(excMsg)
        }

        response.getEntity.writeTo(fos)

        log.info(s"File $localPath downloaded successfully")

      } catch {

        case e: ConnectTimeoutException => {

          if (autoDeleteTempFiles) {

            deleteDownloadTempFile(uuid)
          }
          throw new RuntimeException(e.getMessage)

        }
        case e: Exception => {

          if (autoDeleteTempFiles) {

            deleteDownloadTempFile(uuid)
          }
          throw new RuntimeException(e.getMessage)
        }

      } finally {

        if (client != null) {
          client.close()

        }

        if (fos != null) {

          fos.close()
        }

        if (get != null) {

          get.releaseConnection()
        }

      }
    }

  }

  // mutable map is necessary due to implicit conversion from java.util.Map
  private def hadoopRestUpload(url: Option[String], header: Option[JMap[String, String]], uuid: String): Unit = {

    log.info("In hadoopRestUpload")
    if (url != None && header != None) {

      val localPath = uploadLocalPath(uuid)
      log.info(
        s"""Starting upload to $url
        with header $header
        from local file $localPath
        """.stripMargin)

      // TODO: refactor with try-with-resources (Josh Suereth's scala-arm library)
      var client: CloseableHttpClient = null
      var post: HttpPost = null

      try {

        client = HttpClients.custom().setSSLSocketFactory(sslConnFactory).build()

        //        client = HttpClientBuilder.create().build()

        post = new HttpPost(url.get) {

          setProtocolVersion(HttpVersion.HTTP_1_1) // ensure chunking
          // setEntity(new FileEntity(new File(localPath), ContentType.MULTIPART_FORM_DATA))
          header.get.foreach { case (k, v) => setHeader(k, v) }
        }

        val entity = MultipartEntityBuilder
          .create()
          .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
          .addBinaryBody("file", new File(localPath))
          .build()

        post.setEntity(entity)

        val response = client.execute(post)
        val statusLine = response.getStatusLine
        val statusCode = statusLine.getStatusCode

        if (statusCode != HttpStatus.SC_OK) {

          val excMsg = s"REST upload of R dataset from Alpine to R server failed with status code $statusCode. Message: ${statusLine.getReasonPhrase}. Check R server log for more details."
          log.error(excMsg)
          // This will report the exception in the log
          log.error(IOUtils.toString(response.getEntity.getContent, "UTF-8"))
          throw new RuntimeException(excMsg)
        }

        log.info(s"File $localPath uploaded successfully")

      } catch {

        case e: ConnectTimeoutException => {

          deleteUploadTempFile(uuid)
          throw new RuntimeException(e.getMessage)

        }

        case e: Exception => {

          if (autoDeleteTempFiles) {

            deleteTempFiles(uuid)
          }
          throw new RuntimeException(e)
        }

      } finally {

        if (client != null) {

          client.close()
        }

        if (post != null) {

          post.releaseConnection()
        }
      }
    }
  }

  private def getDBUploadMeta(dfName: String = "alpine_output",
    schemaName: String, tableName: String,
    delimiter: String, quote: String, escape: String,
    limitNum: Long = -1, includeHeader: Boolean): String = {

    def getRTypes(dfName: String = "alpine_output"): JMap[String, Array[String]] = {
      eval(s"as.data.frame(lapply($dfName, class), stringsAsFactors = FALSE)")
        .asInstanceOf[JMap[String, Array[String]]]

    }

    def generateColElem(kv: (String, Array[String]), included: Boolean = true, allowEmpty: Boolean = true) =

      kv match {

        case (k: String, v: Array[String]) => {

          s"""{"columnName":"$k","columnType":"${
            v(0).toLowerCase match {

              case "integer" => "INTEGER"
              case "numeric" => "DOUBLE"
              case "logical" => "BOOLEAN"
              case "character" => "VARCHAR"
              case "factor" => "VARCHAR"
              case _ => "VARCHAR"
            }
          }","isInclude":"$included","allowEmpty":"$allowEmpty"}""".stripMargin
        }
      }

    val getRT = getRTypes(dfName)
    val types = getRT.map(kv => generateColElem(kv)).mkString(",")

    println(s"\n\ntypes\n\n$types\n\n")

    s"""{"schemaName":"$schemaName","tableName":"$tableName","delimiter":"${escapeJava(delimiter)}","quote":"${escapeJava(quote)}","escape":"${escapeJava(escape)}","limitNum":$limitNum,"includeHeader":$includeHeader,"structure":[$types]}""".stripMargin
  }

  private def dbRestUpload(url: Option[String], header: Option[JMap[String, String]], uuid: String,
    delimiterStr: String, quoteStr: String, escapeStr: String,
    schemaName: Option[String], tableName: Option[String]): Unit = {

    log.info("In dbRestUpload")

    if (url != None && header != None && schemaName != None && tableName != None) {

      val localPath = uploadLocalPath(uuid)

      log.info("Making file upload REST call")

      // TODO: refactor with try-with-resources (Josh Suereth's scala-arm library)
      var client: CloseableHttpClient = null
      var post: HttpPost = null

      try {

        client = HttpClients.custom().setSSLSocketFactory(sslConnFactory).build()

        post = new HttpPost(url.get)

        //          header.get.foreach { case (k, v) => post.setHeader(k, v)}

        val entity = MultipartEntityBuilder
          .create()
          .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
          .addBinaryBody("file", new File(localPath))

        // These are form elements, not header elements
        header.get.foreach {
          case (k, v) =>

            entity.addPart(k, new StringBody(v, ContentType.TEXT_PLAIN))

        }

        val metadata = getDBUploadMeta(
          dfName = "alpine_output",
          schemaName = schemaName.get,
          tableName = tableName.get,
          delimiter = delimiterStr,
          quote = quoteStr,
          escape = escapeStr,
          limitNum = -1,
          includeHeader = true
        )

        entity.addPart("fileMetadata", new StringBody(metadata, ContentType.TEXT_PLAIN))

        println(s"\n\nURL is ${url.get} \n\n")

        post.setEntity(entity.build())

        println(s"\n\nMetadata\n\n${metadata.toString}\n\n")

        val response = client.execute(post)
        val statusLine = response.getStatusLine
        val statusCode = statusLine.getStatusCode

        if (statusCode != HttpStatus.SC_OK) {

          val excMsg = s"REST upload of R dataset from Alpine to R server failed with status code $statusCode. Message: ${statusLine.getReasonPhrase}. Check R server log for more details."
          log.error(excMsg)
          // This will report the exception in the log
          log.error(IOUtils.toString(response.getEntity.getContent, "UTF-8"))
          throw new RuntimeException(excMsg)
        }

        log.info(s"File $localPath uploaded successfully")

      } catch {

        case e: Exception => {

          if (autoDeleteTempFiles) {

            deleteTempFiles(uuid)
          }
          throw new RuntimeException(e)
        }

      } finally {

        if (client != null) {

          client.close()
        }

        if (post != null) {

          post.releaseConnection()
        }
      }

    }
  }

  private def deleteTempFile(localPath: String, ifExists: Boolean = true): Unit =
    Try(new File(localPath).delete()) match {

      case Success(_) =>
      case Failure(err) => if (!ifExists) throw new RuntimeException(err.getMessage)
    }

  private def deleteDownloadTempFile(uuid: String): Unit = deleteTempFile(downloadLocalPath(uuid))

  private def deleteUploadTempFile(uuid: String): Unit = deleteTempFile(uploadLocalPath(uuid))

  private def deleteTempFiles(uuid: String): Unit = {

    deleteDownloadTempFile(uuid)
    deleteUploadTempFile(uuid)
  }

  private def enrichRScript(rawScript: String,
    consoleOutputVar: String,
    inputPath: String,
    outputPath: String,
    delimiterStr: String,
    previewNumRows: Long,
    columnNames: Option[JList[String]]): String = {

    def assignColumnNames(objName: String, l: JList[String]) = l.mkString(s"""names($objName) <- c('""", "', '", "')")

    val enrichedScript = s"""
            $consoleOutputVar <- capture.output({

            library(data.table);

            ${if (hasInput(rawScript)) s"alpine_input <- fread(input='$inputPath', sep='$delimiterStr');" else ""}

            ${if (columnNames != None) assignColumnNames("alpine_input", columnNames.get) else ""}

            $rawScript

            ${
      if (hasOutput(rawScript))

        s"""# write temp table to disk
                  write.table(x = alpine_output, file='$outputPath', sep='$delimiterStr', append=FALSE, quote=FALSE, row.names=FALSE)
                  # preview this many rows in UI
                  # need to handle a degenerate case of a single-column data frame, which will be type coerced by R
                  alpineOutputColNames <- names(alpine_output)
                  alpine_output <- alpine_output[1:min($previewNumRows, nrow(alpine_output)),]
                  if (class(alpine_output) != 'data.frame') {
                    alpine_output <- as.data.frame(alpine_output)
                    names(alpine_output) <- alpineOutputColNames
                  }
                """
      else ""
    }
            });""".stripMargin

    log.info(s"Enriched script:\n$enrichedScript")
    enrichedScript
  }

  private def downloadLocalPath(uuid: String) = s"${RServeMain.localTempDir}/$uuid.$downloadExtension"

  private def uploadLocalPath(uuid: String) = s"${RServeMain.localTempDir}/$uuid.$uploadExtension"

  private def hasInput(rScript: String) = Utils.containsNotInComment(rScript, "alpine_input", "#")

  private def hasOutput(rScript: String) = Utils.containsNotInComment(rScript, "alpine_output", "#")

  private def processRequest(uuid: String, rScript: String, returnNames: ReturnNames,
    numPreviewRows: Long, escapeStr: String, delimiterStr: String, quoteStr: String,
    columnNames: Option[JList[String]]): RResponse = {

    var rConsoleOutput: Array[String] = null
    var dataPreview: Option[JMap[String, Object]] = None

    // execute R script
    eval(enrichRScript(rScript, returnNames.rConsoleOutput, downloadLocalPath(uuid),
      uploadLocalPath(uuid), delimiterStr, numPreviewRows, columnNames))

    rConsoleOutput = eval(returnNames.rConsoleOutput).asInstanceOf[Array[String]]

    dataPreview = if (hasOutput(rScript))
      Some(eval(returnNames.outputDataFrame.get).asInstanceOf[JMap[String, Object]])
    else None

    RResponse(rConsoleOutput, dataPreview)

  }

}