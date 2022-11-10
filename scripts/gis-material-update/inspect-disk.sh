#!/bin/sh

docker container create --name dummy \
    -v haitaton_gis_prepare:/haitaton-gis \
    -v $(pwd)/haitaton-gis-output:/gis-output \
    -v $(pwd)/haitaton-downloads:/downloads \
    ubuntu

docker container run --rm -it --volumes-from dummy ubuntu /bin/bash

docker rm dummy