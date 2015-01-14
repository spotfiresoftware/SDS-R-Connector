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
rm -rf prepare_r_component.Rout

echo "Installing Rserve library into R's repository (if necessary) and starting the R-side Rserve component of the service."
echo "The log of the R-side component will can be found in prepare_R_component.Rout"
# Install the Rserve tar ball, ignore installation if it is already installed.
R CMD BATCH prepare_r_component.R

# If there are problems running the R script, print error to screen
if [ $? -ne 0 ]; then
cat prepare_r_component.Rout
echo "Error preparing or installing Rserve"
exit 1
fi

echo "R-side service prepared and installed. Run scripts/start_service.sh to use the R server."