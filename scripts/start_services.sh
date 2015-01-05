#!/bin/bash

echo "Welcome to Alpine R Server"

echo "Checking if R is installed"
# Check if R is installed and visible
R --version &> /dev/null
if [ $? -ne 0 ]; then
	echo "R can't be found. Set up R to be visible before continuing."
	echo "R should be visible on any path via the R command"
	echo "Use 'which R' to check if it's visible."
	exit 1
fi

# Remove old log
rm -rf start_r_component.Rout

echo "Installing Rserve library into R's repository (if necessary) and starting the R-side Rserve component of the service."
echo "The log of the R-side component will can be found in start_R_component.Rout"
# Install the Rserve tar ball, ignore installation if it is already installed.
# Start Rserve on the R side, ignore if it's already running.
R CMD BATCH start_r_component.R

# If there are problems running the R script, print error to screen
if [ $? -ne 0 ]; then
cat start_r_component.Rout
echo "Error installing Rserve and/or starting up the R-side component."
exit 1
fi

echo "R-side service started."

echo "Starting Java-side service. Its log will be found in AlpineRConnector.log"
nohup java -Xmx4096M -Xms1024M -XX:MaxPermSize=512M -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled -Dconfig.file=./application.conf -jar ./alpine-r-connector.jar &> AlpineRConnector.log &

echo $! > java-r-server.pid

echo "Done - check start_R_component.Rout and AlpineRConnector.log if you are experiencing problems."
