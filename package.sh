#!/bin/bash

packaging_folder="r-connector-packaging"
scala_version="2.11"
tsds_version="6.3.2"
jar_name="alpine-r-connector.jar"
tar_name="TIB_sfire-dscr_${tsds_version}_linux_x86_64.tar"
md5_name="${tar_name}.md5"

echo "*** Creating packaging folder ${packaging_folder}"
rm -rf ${packaging_folder}
mkdir ${packaging_folder}

echo "*** Running SBT to assemble jar"
./sbt reload update clean assembly

echo "*** Copying jar ${jar_name} to packaging folder ${packaging_folder}"
cp server/target/scala-${scala_version}/${jar_name} ${packaging_folder}/

echo "*** Copying scripts to packaging folder ${packaging_folder}"
cp -r scripts/* ${packaging_folder}/

echo "*** Creating tarball ${tar_name} from folder ${packaging_folder}"
rm -f ${tar_name}
tar cvfh ${tar_name} --directory="${packaging_folder}" .

echo "*** Creating md5 signature file ${md5_name} from tarball ${tar_name}"
rm -f ${md5_name}
md5 ${tar_name} > ${md5_name}

echo "*** Removing packaging folder ${packaging_folder}"
rm -rf ${packaging_folder}
