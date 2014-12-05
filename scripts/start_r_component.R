# Alpine R server script to install R-side listener and start the R server in the background
# Copyright 2014 Alpine Data Labs

isInstalled <- function(mypkg){
  is.element(mypkg, installed.packages()[,1])
} 

if (!isInstalled('Rserve')) {
  print('Installing the R-side Rserve component to local R repository')
} 

fullPath <- function(fileName) {
	return c(paste(getwd(), "/", fileName, sep = "")
}

install.packages(pkgs = c(fullPath("Rserve_1.7-3.tar.gz"), fullPath("data.table_1.9.4.tar.gz")), repos = NULL, type="source")
library(Rserve)
library(data.table)

print('Starting R server on the R side - now start the Alpine R server')

Rserve(args = "--no-save")