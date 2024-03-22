FROM python:3.10

RUN apt-get update  \
    && apt-get install --no-install-recommends -y \
    gdal-bin \
    gettext-base \
    libgdal-dev \
    python3-launchpadlib  \
    software-properties-common \
    wget \
    && apt-get clean
RUN add-apt-repository ppa:rmescandon/yq
RUN apt-get install --no-install-recommends -y yq && apt-get clean

ENTRYPOINT [ "sh", "/haitaton-gis/fetch_all.sh" ]