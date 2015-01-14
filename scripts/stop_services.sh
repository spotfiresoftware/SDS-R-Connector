#!/bin/bash

# kill Rserve
echo "Killing Rserve"
killall Rserve

# kill Java process
echo "Killing the Scala web server"
cat java-r-server.pid | xargs kill -9
rm java-r-server.pid
