FROM golang:1.14-stretch
RUN apt-get update && apt-get install git 

RUN go get github.com/richardlehane/siegfried/cmd/sf && /go/bin/sf -update
CMD cp /root/siegfried/default.sig /tmp/fileformatbuild && cp /go/bin/sf /tmp/fileformatbuild
