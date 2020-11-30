FROM alpine
RUN addgroup --system fileformatgroup && adduser --system fileformatuser -G fileformatgroup
RUN apk update && apk add git unzip wget
RUN wget -qq https://github.com/digital-preservation/droid/releases/download/droid-6.5/droid-binary-6.5-bin.zip
COPY droid.sh /

USER fileformatuser
CMD unzip -o -d /tmp/fileformatbuild droid-binary-6.5-bin.zip && cp /droid.sh /tmp/fileformatbuild/droid
