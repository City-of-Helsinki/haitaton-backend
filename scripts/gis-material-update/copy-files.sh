#!/bin/sh

docker container create --name dummy -v haitaton_gis_prepare:/haitaton-gis alpine
docker cp fetch/fetch_data.sh dummy://haitaton-gis
docker cp fetch/fetch_all.sh dummy://haitaton-gis

docker cp process/process_data.sh dummy://haitaton-gis
docker cp process/process_data.py dummy://haitaton-gis
docker cp process/modules/. dummy://haitaton-gis/modules

for f in process/*.py;
do
    docker cp "${f}" dummy://haitaton-gis/
done

docker cp config.yaml dummy://haitaton-gis

docker rm dummy