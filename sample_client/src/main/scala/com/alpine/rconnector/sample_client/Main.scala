package com.alpine.rconnector.client

import com.typesafe.config.ConfigFactory
import akka.actor.{ Props, ActorSystem }
import com.alpine.rconnector.messages.{ RStart, RStop, RRequest }
import com.alpine.rconnector.server.RServeMain
import scala.collection.JavaConversions._

object Main extends App {

  RServeMain.startup()

  Thread.sleep(1000)

  val config = ConfigFactory.load()

  val system = ActorSystem.create("local", config.getConfig("clientApp"))
  val client = system.actorOf(Props(new Client("akka.tcp://rServeActorSystem@127.0.0.1:2553/user/master")), "client")

  Thread.sleep(5000)

  Thread.sleep(5000)

  client ! RStop
  Thread.sleep(5000)
  client ! RStart
  Thread.sleep(5000)
  client ! RRequest("uuid123", "x = mean(1:10)", Array("x"))
  Thread.sleep(5000)

  system.shutdown()
  RServeMain.shutdown()
}