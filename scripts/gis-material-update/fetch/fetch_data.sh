#!/bin/sh

config_yaml="/haitaton-gis/config.yaml"

# Parse configuration YAML
# Built using:
# yq --version
# yq (https://github.com/mikefarah/yq/) version 4.16.2

data_object=$1

parse_config () {
    parsed_value=$(yq eval -e ".${1}" - < $config_yaml) > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        retval="$parsed_value"
    else
        retval=""
    fi
    echo "$retval"
}

# dynamic configuration
# refer to configuration YAML

cfg_addr () {
    echo "${data_object}.addr"
}

cfg_local_file () {
    echo "${data_object}.local_file"
}

cfg_layer () {
    echo "${data_object}.layer"
}

# if we have progressed this far, object is supported in configuration
# now it remains to find out how to download files
addr=$(parse_config $(cfg_addr $data_object))
layer=$(parse_config $(cfg_layer $data_object))
local_file=$(parse_config $(cfg_local_file $data_object))

case $data_object in
hsl|osm)
    echo wget -O "$local_file $addr"
    wget -O "$local_file" "$addr"
    ;;
ylre_katualue|ylre_katuosat|maka_autoliikennemaarat)
    ogr2ogr -f GPKG "$local_file" WFS:"$addr" "$layer"
    ;;
*)
    echo "Not supported"
    return 1
    ;;
esac
# https://stackoverflow.com/questions/22049212/docker-copying-files-from-docker-container-to-host