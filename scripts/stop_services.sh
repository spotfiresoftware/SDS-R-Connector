#!/bin/bash

# kill Rserve
echo "Killing Rserve"
killall Rserve

echo ""

# kill Java process
echo "Killing the Scala web server"
cat java-r-server.pid | xargs kill -9
rm -f java-r-server.pid
