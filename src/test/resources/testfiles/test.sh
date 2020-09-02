#!/bin/bash
if [ "$2" = "-v" ];
then
  echo '2020-09-02T12:46:21,181  INFO [main] DroidCommandLine:140 - Starting DROID.
6.5'
elif [ "$2" = "-x" ];
then
  echo '2020-09-02T12:48:36,263  INFO [main] DroidCommandLine:140 - Starting DROID.
Type: Container Version:  20200121  File name: container-signature-20200121.xml
Type: Binary Version:  96  File name: DROID_SignatureFile_V96.xml'
else
  cp ./src/test/resources/testfiles/$1 ./result.csv
fi

