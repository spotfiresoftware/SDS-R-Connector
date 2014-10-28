#!/bin/bash
java -Xmx1024M -Xms128M -XX:MaxPermSize=512M -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled -jar sbt-launch.jar $@