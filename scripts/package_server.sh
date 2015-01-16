#!/bin/bash

echo "Packaging all resources for Alpine R Server."

echo "Building new scala webserver version with sbt server/assembly"
sbt server/assembly
if [ $? -ne 0 ]; then
	echo "Error building scala webserver"
	exit 1
fi


# copy necessary files to alpine-r-connector
mkdir -p alpine-r-connector
cp scripts/start_services.sh alpine-r-connector/
cp scripts/stop_services.sh alpine-r-connector/
cp scripts/prepare_services.sh alpine-r-connector/
cp scripts/license.txt alpine-r-connector/
cp scripts/*.tar.gz alpine-r-connector/
cp scripts/*.R alpine-r-connector/
cp scripts/application.conf alpine-r-connector

# make a simple README
echo "The Alpine R Server.\n\nFor first time installation, run prepare_services.sh\n" >> alpine-r-connector/README
echo "To start and stop the R server, execute start_services.sh and stop_services.sh, respectively." >> alpine-r-connector/README

# create archive and clean up dir
tar -cf alpine-r-connector.tar alpine-r-connector
rm -rf alpine-r-connector


echo "Done. alpine-r-connector.tar has all packaged materials."

