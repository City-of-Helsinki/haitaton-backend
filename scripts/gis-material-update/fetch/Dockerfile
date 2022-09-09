# TODO: fix version
FROM ubuntu

RUN apt-get update && \
    apt-get install -y \
    software-properties-common wget gdal-bin
RUN add-apt-repository ppa:rmescandon/yq
RUN apt install yq

COPY ./fetch_all.sh /
COPY ./fetch_data.sh /
COPY ./config.yaml /
RUN mkdir /downloads

ENTRYPOINT [ "sh", "/fetch_all.sh" ]