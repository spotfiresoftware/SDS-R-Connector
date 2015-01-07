package com.alpine.rconnector.server

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * @author Marek Kolodziej
 */
class DefaultTrustManager extends X509TrustManager {

  override def checkClientTrusted(p1: Array[X509Certificate], p2: String): Unit = {}

  override def checkServerTrusted(p1: Array[X509Certificate], p2: String): Unit = {}

  override def getAcceptedIssuers: Array[X509Certificate] = null
}
