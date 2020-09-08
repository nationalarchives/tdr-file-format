FROM openjdk:8-jdk
RUN apt-get update && apt-get install -y git unzip wget
RUN wget -qq https://github.com/digital-preservation/droid/releases/download/droid-6.5/droid-binary-6.5-bin.zip
COPY droid.sh /
CMD unzip -o -d /tmp/fileformatbuild droid-binary-6.5-bin.zip && cp /droid.sh /tmp/fileformatbuild/droid