FROM python:3.10

WORKDIR /opt
COPY ./process/requirements.txt /opt
RUN python -m venv /opt/venv && /opt/venv/bin/pip install --upgrade pip && /opt/venv/bin/pip install -r requirements.txt
WORKDIR /haitaton-gis

ENTRYPOINT [ "sh", "/haitaton-gis/process_data.sh" ]