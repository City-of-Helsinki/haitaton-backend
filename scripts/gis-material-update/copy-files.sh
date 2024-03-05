#!/bin/sh

docker container create --name dummy -v haitaton_gis_prepare:/haitaton-gis -v haitaton_gis_validate_deploy:/haitaton-gis-validate-deploy alpine
docker cp fetch/fetch_data.sh dummy://haitaton-gis
docker cp fetch/fetch_all.sh dummy://haitaton-gis

docker cp process/process_data.sh dummy://haitaton-gis
docker cp process/process_data.py dummy://haitaton-gis
docker cp process/modules/. dummy://haitaton-gis/modules

docker cp validate-deploy/validate_deploy_data.sh dummy://haitaton-gis-validate-deploy
docker cp validate-deploy/validate_deploy_data.py dummy://haitaton-gis-validate-deploy
docker cp validate-deploy/modules/. dummy://haitaton-gis-validate-deploy/modules
for f in process/*.py;
do
    docker cp "${f}" dummy://haitaton-gis/
done
for f in validate-deploy/*.py;
do
    docker cp "${f}" dummy://haitaton-gis-validate-deploy/
done
docker cp config.yaml dummy://haitaton-gis
docker cp config.yaml dummy://haitaton-gis-validate-deploy
docker cp osm_vrt_clip.vrt dummy://haitaton-gis

docker rm dummy