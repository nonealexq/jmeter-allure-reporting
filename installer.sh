#!/bin/sh

#Author: Alexey Chichuk
#Description: Main steps to start use these scripts

_PWD=$(pwd)
_JMETER_VERSION="5.4.1"
_JMETER_DOWNLOAD_URL="https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$_JMETER_VERSION.tgz"


echo "\nDownloading the binary from"  $_JMETER_DOWNLOAD_URL
curl -L --silent $_JMETER_DOWNLOAD_URL -k > apache-jmeter-$_JMETER_VERSION.tgz

echo "\nUnzipping .tgz binary..."
  eval tar zxvf $_PWD/apache-jmeter-$_JMETER_VERSION.tgz
  mv apache-jmeter-$_JMETER_VERSION apache-jmeter

echo "\nCopy libriries ..."
  cp -a $_PWD/jmeter/lib/. $_PWD/apache-jmeter/lib
  cp -rf $_PWD/jmeter/jmeter.properties $_PWD/apache-jmeter/bin

echo "\nRemove binary tgz ..."
  rm -rf apache-jmeter-$_JMETER_VERSION.tgz

sleep 2
echo "\ndone!"
