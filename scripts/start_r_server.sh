#!/bin/bash

nohup java -Dconfig.file=./application.conf -jar ./server-assembly-0.5.jar &> AlpineRConnector.log &

