# Alpine R server script to install R-side listener and start the R server in the background
# Copyright 2014 Alpine Data Labs

isInstalled <- function(mypkg) {
  is.element(mypkg, installed.packages()[,1])
}

fullPath <- function(fileName) {
  paste(getwd(), "/", fileName, sep = "")
}

# Note: order is significant due to the dependency graph
packageList = list(Rcpp = "Rcpp_0.11.3.tar.gz", 
                   plyr = "plyr_1.8.1.tar.gz",
                   stringr = "stringr_0.6.2.tar.gz",
                   chron = "chron_2.3-45.tar.gz",
                   reshape2 = "reshape2_1.4.1.tar.gz",
                   Rserve = "Rserve_1.7-3.tar.gz",
                   data.table = "data.table_1.9.4.tar.gz")

for (pkg in names(packageList)) {
  
  if (!isInstalled(pkg)) {
    
    print(sprintf("Package %s is not installed. Installing now.", pkg))
    install.packages(pkgs = fullPath(packageList[[pkg]]), repos = NULL, type = "source")

  } else {

    print(sprintf("Package %s is already installed. No need to re-install.", pkg))
  }
}

library(Rserve)

print('Starting R server on the R side - now start the Alpine R server')

Rserve(args = "--no-save")
