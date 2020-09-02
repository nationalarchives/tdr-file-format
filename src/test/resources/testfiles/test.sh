#!/bin/bash
if [ "$1" = "-v" ];
then
  echo '2020-09-02T12:46:21,181  INFO [main] DroidCommandLine:140 - Starting DROID.
6.5'
elif [ "$1" = "-x" ];
then
  echo '2020-09-02T12:48:36,263  INFO [main] DroidCommandLine:140 - Starting DROID.
Type: Container Version:  20200121  File name: container-signature-20200121.xml
Type: Binary Version:  96  File name: DROID_SignatureFile_V96.xml'
else
  echo '"ID","PARENT_ID","URI","FILE_PATH","NAME","METHOD","STATUS","SIZE","TYPE","EXT","LAST_MODIFIED","EXTENSION_MISMATCH","HASH","FORMAT_COUNT","PUID","MIME_TYPE","FORMAT_NAME","FORMAT_VERSION"
"2","","file:/home/sam/utils/testfiles/hybrid_jpeg_html_file.jpg","/home/sam/utils/testfiles/hybrid_jpeg_html_file.jpg","hybrid_jpeg_html_file.jpg","Signature","Done","60","File","jpg","2020-07-17T16:31:01","true","","2","fmt/96","text/html","Hypertext Markup Language",""
' > result.csv
fi

