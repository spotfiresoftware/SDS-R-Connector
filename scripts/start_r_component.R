# Alpine R server script to install R-side listener and start the R server in the background
# Copyright 2014 Alpine Data Labs

isInstalled <- function(mypkg){
  is.element(mypkg, installed.packages()[,1])
} 

if (!isInstalled('Rserve')) {
  print('Installing the R-side Rserve component to local R repository')
} 

install.packages(paste(getwd(), "/Rserve_1.7-3.tar.gz", sep = ""), repos = NULL, type="source")
library(Rserve)

print('Starting R server on the R side - now start the Alpine R server')

Rserve(args = "--no-save")