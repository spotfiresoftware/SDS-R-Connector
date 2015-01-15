package com.alpine.rconnector.server

import java.io.File
import java.net.{ BindException, ServerSocket }

import scala.util.{ Failure, Success, Try }
import org.apache.http.HttpStatus

/**
 * @author Marek Kolodziej
 */
object Utils {

  /**
   * Check if directory exists. Create if it doesn't, fail otherwise.
   * Also fail if file exists but it isn't a directory, and if
   * Java has no read/write permissions.
   *
   * @param path
   */
  def checkTempDirReadWrite(path: String): Unit = {

    val dir = new File(path)

    // Exit if it's not an absolute path
    if (!dir.isAbsolute) {
      sys.error(s"Directory $path is not an absolute path. Fix your configuration.")
      sys.exit(1)
    }

    // Fail if file exists but it's not a dir
    if (dir.exists && !dir.isDirectory) {

      sys.error(s"Path $path exists but it's not a directory.")
      sys.exit(1)
    }

    // If dir doesn't exist, create it if you can, fail otherwise
    if (!dir.exists) {

      Try(dir.mkdirs()) match {

        case Success(status) if status => // nothing, it's OK

        case Success(status) if !status => {

          sys.error(s"Directory $path did not exist but could not create it. Check your permissions.")
          sys.exit(1)
        }

        case Failure(t) => {

          sys.error(s"Directory $path could not be created due to a SecurityException.")
          sys.error(s"Cause:\n${t.getCause}")
          sys.error(s"Message:\n${t.getMessage}")
          sys.error(t.getStackTrace.mkString("\n"))
          sys.exit(1)
        }
      }
    }

    if (!dir.canRead) {

      sys.error(s"Directory $path exists but reading is prohibited. Check your permissions.")
      sys.exit(1)
    }

    if (!dir.canWrite) {

      sys.error(s"Directory $path exists but writing is prohibited. Check your permissions.")
    }

  }

  /**
   * Old Akka versions will hang and not exit the program if the socket is already bound.
   * Check if the socket is bound and if it is, do a System.exit(1).
   * If the socket is free, close it and let Akka do its thing.
   * @param port
   */
  def isSocketFree(port: Int): Unit = {

    Try(new ServerSocket(port)) match {
      case Success(socket) => socket.close()
      case Failure(e: BindException) => {
        sys.error(s"Port $port is already bound")
        sys.exit(1)
      }
      case Failure(e) => {
        sys.error(e.getMessage)
        e.printStackTrace()
        sys.exit(1)
      }
    }
  }

  /**
   * Check Java version - 1.6 is a minimum requirement
   */
  def ensureJavaVersion(): Unit = {
    """(\d+\.\d+)\.?(\w+)?""".r.unapplySeq(System.getProperty("java.version")) match {
      case None => {
        sys.error("Java version not recognized")
        sys.exit(1)
      }
      case Some(lst) => {
        val ver = lst(0).toDouble
        if (ver >= 1.6) {
          println(s"Java version $ver is OK (>= 1.6)")
        } else {
          sys.error(s"Java version $ver is inadequate (should be >= 1.6)")
          sys.exit(1)
        }
      }
    }
  }

  def containsNotInComment(source: String, matcher: String, comment: String): Boolean = {

    val rows: Array[String] = source.split("\n")

    rows.map(_.replaceAll("\\s+", "")).exists(row => {
      val matcherIdx: Int = row.indexOf(matcher)
      val commentIdx: Int = row.indexOf(comment)

      println(s"commentIdx = $commentIdx, matcherIdx = $matcherIdx")

      commentIdx < 0 && matcherIdx >= 0 || commentIdx >= 0 && matcherIdx >= 0 && commentIdx > matcherIdx
    })
  }

  def alpineUpDownLoadErrMsg(msgPrefix: String)(statusCode: Int, responseContent: String): String = {

    val reason = statusCode match {
      case HttpStatus.SC_INTERNAL_SERVER_ERROR => {
        responseContent.split("\n")
          .flatMap(x => Try(x.split("The server encountered an internal error ")(1)).toOption)
          .map(_.trim)
          .filter(_.size > 0)
          .mkString("|")
      }
      case _ => (s: String) => s
    }

    s"$msgPrefix, status code $statusCode : $reason"
  }

}
