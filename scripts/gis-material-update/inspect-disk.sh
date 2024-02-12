#!/bin/sh

docker container create --name dummy \
    -v haitaton_gis_prepare:/haitaton-gis \
    -v haitaton_gis_validate_deploy:/haitaton-gis-validate-deploy \
    -v $(pwd)/haitaton-gis-output:/gis-output \
    -v $(pwd)/haitaton-downloads:/downloads \
    -v $(pwd)/haitaton-gis-log:/gis-log \
    ubuntu

docker container run --rm -it --volumes-from dummy ubuntu /bin/bash

docker rm dummy