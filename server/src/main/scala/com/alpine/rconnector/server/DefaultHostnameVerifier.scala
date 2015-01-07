package com.alpine.rconnector.server

import javax.net.ssl.{ SSLSession, SSLSocket }
import java.security.cert.X509Certificate

import org.apache.http.conn.ssl.X509HostnameVerifier

/**
 *
 */
class DefaultHostnameVerifier extends X509HostnameVerifier {

  override def verify(host: String, ssl: SSLSocket): Unit = {}

  override def verify(host: String, cns: Array[String], subjectAlts: Array[String]): Unit = {}

  override def verify(host: String, cert: X509Certificate): Unit = {}

  override def verify(hostname: String, session: SSLSession): Boolean = true

}
