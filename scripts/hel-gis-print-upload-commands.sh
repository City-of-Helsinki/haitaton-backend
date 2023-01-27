#!/bin/sh

# Print upload commands to stdout

# common configuration
config_yaml="./hel-gis-data-variables.yaml"

# source directory name
SOURCEDIR=gis-material-update/haitaton-gis-output

usage () {
    echo "Usage: $0"
    exit 2
}

. ./parse_config_common.sh

if [ $# -ne 0 ]; then
    usage
fi

# check oc status
# User should be logged in to find out pod name
if ! $(oc whoami > /dev/null 2>&1 ); then
    echo "oc: not logged in"
    exit 3
fi

# All tormays-polygon files
FILES=$(parse_config "files")

# file share address pod name
FILESHARE_NAME=$(oc get pods | grep fileshare | grep Running | head -1 | awk '{print $1}')

# file count for loop reference
# This approach does not support file names with spaces
file_count=$(echo $FILES | wc -w)

counter=0
for file in $FILES; do
    counter=$((counter + 1))
    layer=${file%.gpkg}
    if [ -f ${SOURCEDIR}/$file ]; then
        echo "# [$counter/$file_count] Uploading: '$file', layer: '$layer'"
        echo "oc cp ${SOURCEDIR}/$file ${FILESHARE_NAME}:/var/www/html/public"
        echo ""
    else
        echo "# [$counter/$file_count] Not uploading: (processed file does not exist: '$file')"
    fi
done
