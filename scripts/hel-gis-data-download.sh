#!/bin/bash

# Script to download tormays polygons GIS material from project fileshare

set -e

# Kudos: https://stackoverflow.com/questions/2683279/how-to-detect-if-a-script-is-being-sourced
(return 0 2>/dev/null) && sourced=1 || sourced=0

if [ "$sourced" -eq "1" ] || ! test -z "$(type -p)" ; then
    echo "Use bash to run script."
    return 0
fi

# Common variables are fetched from separate file
. ./hel-gis-data-variables.sh

FILESHARE_ADDR="http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi"

if ! [ -d "$POLYS_DIR" ]; then
    mkdir "$POLYS_DIR"
fi

# Needs maintenance VPN to access the fileshare.
start=`date +%s`

counter=0
for file in ${FILES[@]}; do
    counter=$((counter + 1))
    echo "[$counter/${#FILES[@]}] Downloading ${file} to $POLYS_DIR"
    wget --show-progress "${FILESHARE_ADDR}/${file}" -O "${POLYS_DIR}/${file}"
    echo "Downloaded ${file}"
done

end=`date +%s`
runtime=$((end-start))
rt=$(echo "scale=2; $runtime / 60" | bc -l)
echo "Download took $rt minutes"
echo "Files are downloaded to directory: ${POLYS_DIR}"
