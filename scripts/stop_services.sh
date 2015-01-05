#!/bin/bash

# kill Rserve
killall Rserve

# kill Java process
cat java-r-server.pid | xargs kill -9
