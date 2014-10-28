#!/bin/bash

nohup java -Xmx4096M -Xms1024M -XX:MaxPermSize=512M -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled -Dconfig.file=./application.conf -jar ./alpine-r-connector.jar &> AlpineRConnector.log &

