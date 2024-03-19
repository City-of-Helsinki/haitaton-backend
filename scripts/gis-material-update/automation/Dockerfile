FROM python:3.10

ENV TORMAYS_DEPLOYMENT_PROFILE=docker_development

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

WORKDIR /opt
COPY ./automation/requirements.txt /opt
RUN python -m venv /opt/venv && /opt/venv/bin/pip install --upgrade pip && /opt/venv/bin/pip install -r requirements.txt

COPY ./automation/automation.sh /haitaton-automation/
COPY ./config.yaml /haitaton-gis/
COPY ./config.yaml /haitaton-gis-validate-deploy/
COPY ./automation/fetch_all.sh /haitaton-gis/
COPY ./automation/fetch_data.sh /haitaton-gis/
COPY ./process/process_data.py /haitaton-gis/
COPY ./process/process_data.sh /haitaton-gis/
COPY ./osm_vrt_clip.vrt /haitaton-gis/
COPY ./process/modules/ /haitaton-gis/modules/
COPY ./validate-deploy/validate_deploy_data.py /haitaton-gis-validate-deploy/
COPY ./validate-deploy/validate_deploy_data.sh /haitaton-gis-validate-deploy/
COPY ./validate-deploy/modules/ /haitaton-gis-validate-deploy/modules/
RUN mkdir -p /downloads /gis-output /local_data
WORKDIR /haitaton-automation

ENTRYPOINT [ "sh", "/haitaton-automation/automation.sh" ]
