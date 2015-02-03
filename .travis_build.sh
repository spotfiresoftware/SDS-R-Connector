#!/bin/sh
sbt server/assembly
sbt coveralls
