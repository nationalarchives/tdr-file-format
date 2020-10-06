#!/bin/bash
# This script emulates droid within the tests as droid itself is too slow to run inside the tests.
# Droid is called four times. Once with -v for the droid version, once with -x for the signature file version, once with
# -a where the file is added to a Droid profile and once with -p where the profile is converted to a csv
if [ "$2" = "-v" ];
then
  echo '2020-09-02T12:46:21,181  INFO [main] DroidCommandLine:140 - Starting DROID.
6.5'
elif [ "$2" = "-x" ];
then
  echo '2020-09-02T12:48:36,263  INFO [main] DroidCommandLine:140 - Starting DROID.
Type: Container Version:  20200121  File name: container-signature-20200121.xml
Type: Binary Version:  96  File name: DROID_SignatureFile_V96.xml'
elif [ "$2" = "-a" ];
then
  # Droid will check to see if the file passed as the third argument exists. If it doesn't it will error.
  # This call do ls will do the same. The files are created before each test in the FileSpec trait.
  ls "$3"
else
  mkdir -p ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/acea5919-25a3-4c6b-8908-fa47cc77878f
  cp ./src/test/resources/testfiles/$1 ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/acea5919-25a3-4c6b-8908-fa47cc77878f.csv
fi

