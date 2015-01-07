package com.alpine.rconnector.server

import java.net.ProtocolException

import org.apache.http.{ HttpRequest, HttpResponse }
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.protocol.HttpContext
import org.apache.http.client.methods.{ HttpGet, HttpHead, HttpPost }

@throws[ProtocolException]
class LaxPostRedirectStrategy extends LaxRedirectStrategy {

  override def getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest = {

    val uri = getLocationURI(request, response, context)
    val method = request.getRequestLine().getMethod()
    method match {

      case HttpGet.METHOD_NAME => new HttpGet(uri)
      case HttpHead.METHOD_NAME => new HttpHead(uri)
      case HttpPost.METHOD_NAME => new HttpPost(uri)
    }
  }

}
