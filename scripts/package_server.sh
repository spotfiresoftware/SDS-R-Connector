#!/bin/bash

echo "Packaging all resources for Alpine R Server."

echo "Building new scala webserver version with sbt server/assembly..."
SLOG=.server_assembly.log
sbt server/assembly &> $SLOG
if [ $? -ne 0 ]; then
	cat $SLOG
	echo "** ERROR Packaging Alpine R Server. Webserver compilation & packaging failed. **"
	rm $SLOG
	exit 1
fi

# copy necessary files to alpine-r-connector
mkdir -p alpine-r-connector
cp scripts/start_services.sh alpine-r-connector/
cp scripts/stop_services.sh alpine-r-connector/
cp scripts/prepare_services.sh alpine-r-connector/
cp scripts/LICENSE alpine-r-connector/
cp scripts/*.tar.gz alpine-r-connector/
cp scripts/*.R alpine-r-connector/
cp scripts/application.conf alpine-r-connector

# make a simple README
README="Official Alpine R Server, version 5.1 Shastina.

Unpackage with

    tar xf alpine-r-connectorfigu

For first time installation, run

    ./prepare_services.sh

After this step, the R server (both the R process and the webserver) can be started and stopped by executing the following scripts

    ./start_services.sh
    ./stop_services.sh

The R server has several deployment configuration options. These are specified in the application.conf file. For more information about deployment and other related topics, consult the [README](https://github.com/alpinedatalabs/alpine-r/blob/master/README.md) in the alpine-r project."
echo $README > ./alpine-r-connector/README.md

# create archive and clean up dir
tar -cf alpine-r-connector.tar alpine-r-connector
rm -rf alpine-r-connector

echo "Done. alpine-r-connector.tar has all packaged materials."
