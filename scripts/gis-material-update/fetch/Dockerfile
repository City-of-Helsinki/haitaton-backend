# TODO: fix version
FROM ubuntu

RUN apt-get update && \
    apt-get install -y \
    software-properties-common wget gdal-bin gettext-base
RUN add-apt-repository ppa:rmescandon/yq
RUN apt install yq

ENTRYPOINT [ "sh", "/haitaton-gis/fetch_all.sh" ]