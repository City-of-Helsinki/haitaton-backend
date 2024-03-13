FROM python:3.10

ENV TORMAYS_DEPLOYMENT_PROFILE=docker_development
RUN apt-get update

RUN apt-get install -y software-properties-common wget gdal-bin libgdal-dev gettext-base python3-launchpadlib
RUN add-apt-repository ppa:rmescandon/yq
RUN apt install -y yq

WORKDIR /opt
COPY ./automation/requirements.txt /opt
RUN python -m venv /opt/venv && /opt/venv/bin/pip install --upgrade pip && /opt/venv/bin/pip install -r requirements.txt
WORKDIR /haitaton-automation
COPY ./automation/requirements.txt .
COPY ./automation/automation.sh .
WORKDIR /haitaton-gis
COPY ./config.yaml .
COPY ./fetch/fetch_all.sh .
COPY ./fetch/fetch_data.sh .
COPY ./process/process_data.py .
COPY ./process/process_data.sh .
WORKDIR /haitaton-gis/modules
COPY ./process/modules/ .
WORKDIR /haitaton-gis-validate-deploy
COPY ./config.yaml .
COPY ./validate-deploy/validate_deploy_data.py .
COPY ./validate-deploy/validate_deploy_data.sh .
WORKDIR /haitaton-gis-validate-deploy/modules
COPY ./validate-deploy/modules/ .
WORKDIR /downloads
WORKDIR /local_data
WORKDIR /gis-output
WORKDIR /haitaton-automation

ENTRYPOINT [ "sh", "/haitaton-automation/automation.sh" ]
