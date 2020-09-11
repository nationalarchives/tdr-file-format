#!/bin/sh
java -Xmx512m -DdroidTempDir=/mnt/fileformat -DdroidLogDir=/mnt/fileformat -DdroidUserDir=/mnt/fileformat -jar "/mnt/fileformat/droid-command-line-6.5.jar" "$@"

