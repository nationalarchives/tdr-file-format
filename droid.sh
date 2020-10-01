#!/bin/sh
java -Xmx512m -DdroidTempDir=/mnt/backend-checks -DdroidLogDir=/mnt/backend-checks -DdroidUserDir=/mnt/backend-checks -jar "/mnt/backend-checks/droid-command-line-6.5.jar" "$@"

