FROM alpine
RUN addgroup --system fileformatgroup && adduser --system fileformatuser -G fileformatgroup
RUN apk update \
    && apk add git unzip wget \
    && apk upgrade apk-tools busybox expat
RUN wget -qq https://cdn.nationalarchives.gov.uk/documents/droid-binary-6.5.1-bin.zip
COPY droid.sh /

USER fileformatuser
CMD unzip -o -d /tmp/fileformatbuild droid-binary-6.5.1-bin.zip && cp /droid.sh /tmp/fileformatbuild/droid
