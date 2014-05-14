package com.alpine.rconnector.client

import com.typesafe.config.ConfigFactory
import akka.actor.{ Props, ActorSystem }
import com.alpine.rconnector.messages.{ RStart, RStop, RRequest }
import com.alpine.rconnector.server.RMicrokernelMaster

object Main extends App {

  val test = new RMicrokernelMaster()
  test.startup()

  Thread.sleep(1000)

  val config = ConfigFactory.load()

  val system = ActorSystem.create("local", config.getConfig("clientApp"))
  val client = system.actorOf(Props(new Client("akka.tcp://rServeActorSystem@127.0.0.1:2553/user/master")), "client")

  Thread.sleep(5000)

  /*
    val rScript =
      """
        |library(RCurl)
        |foo = read.csv(text=getURL(url='http://hci.stanford.edu/jheer/workshop/data/worldbank/worldbank.csv'))
        |bar = sample(x=foo$Energy.use..kg.of.oil.equivalent.per.capita,replace=FALSE, size = 100)
        |bar = bar[!is.na(bar)]
        |mean(bar)
      """.stripMargin
  */

  val rScript = "foo"

  for (i <- 1 to 2) {

    client ! RRequest(rScript)
  }

  Thread.sleep(5000)

  client ! RStop
  Thread.sleep(5000)
  client ! RStart
  Thread.sleep(5000)
  client ! RRequest("mean(1:10)")
  Thread.sleep(5000)

  system.shutdown()
  test.shutdown()
}