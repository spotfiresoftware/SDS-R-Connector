#!/bin/bash

# kill Rserve
echo "Killing Rserve"
killall Rserve

echo ""

# kill Java process
echo "Killing the Scala web server"
kill -9 `cat java-r-server.pid`
rm -f java-r-server.pid
