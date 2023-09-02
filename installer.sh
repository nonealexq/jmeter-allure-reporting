#!/bin/sh

#Author: Alexey Chichuk
#Description: Main steps to start use these scripts

_PWD=$(pwd)
_JMETER_VERSION="5.6.2"

#If you want to set default prefix for test_fragments - uncomment/change it
#_TEST_FRAGMENTS_FOLDER="$_PWD/test-plan/test_fragments/"

#URL from official apache mirror
_JMETER_DOWNLOAD_URL="https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$_JMETER_VERSION.tgz"

#Clear old binary if exists
echo "Binary update is needed"
if [ "$(ls -l | grep -c "apache-jmeter")" -ge 1 ]; then
  echo "\nRemoving folder apache-jmeter ...."
    rm -rf $_PWD/apache-jmeter/
fi

#Downliading
echo "\nDownloading the binary from"  $_JMETER_DOWNLOAD_URL
curl -L --silent $_JMETER_DOWNLOAD_URL -k > apache-jmeter-$_JMETER_VERSION.tgz

#Unzipping binary
echo "\nUnzipping .tgz binary..."
  eval tar zxvf $_PWD/apache-jmeter-$_JMETER_VERSION.tgz
  mv apache-jmeter-$_JMETER_VERSION apache-jmeter

#Copy own libriries
echo "\nCopy libriries ..."
  cp -a $_PWD/jmeter/lib/. $_PWD/apache-jmeter/lib
  rm $_PWD/apache-jmeter/lib/tika-core-1.28.5.jar $_PWD/apache-jmeter/lib/tika-parsers-1.28.5.jar

#Change jmeter.properties to own
  cp -rf $_PWD/jmeter/jmeter.properties $_PWD/apache-jmeter/bin

#If you want to set default prefix for test_fragments - uncomment/change it
#echo "\nUpdate includecontroller.prefix ..."
#sed -i -e "s%#includecontroller.prefix=%includecontroller.prefix=$_TEST_FRAGMENTS_FOLDER%g" \
#  $_PWD/apache-jmeter/bin/jmeter.properties

#Removing tmp tgz file
echo "\nRemove binary tgz ..."
  rm -rf apache-jmeter-$_JMETER_VERSION.tgz

sleep 2
echo "\ndone!"
