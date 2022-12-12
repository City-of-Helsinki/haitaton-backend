#!/bin/bash

# Script to import downloaded GIS material to local development database

set -e

# Kudos: https://stackoverflow.com/questions/2683279/how-to-detect-if-a-script-is-being-sourced
(return 0 2>/dev/null) && sourced=1 || sourced=0

if [ "$sourced" -eq "1" ] || ! test -z "$(type -p)" ; then
    echo "Use bash to run script."
    return 0
fi

# Common variables are fetched from dedicated file
. ./hel-gis-data-variables.sh

DB_NAME="haitaton"
DB_USER="haitaton_user"
DB_PASS="haitaton"
DB_HOST="db"
DB_PORT="5432"

start=`date +%s`

counter=0
for file in ${FILES[@]}; do
    counter=$((counter + 1))
    if [ -f "${POLYS_DIR}/${file}" ]; then
        echo "[$counter/${#FILES[@]}] Importing '${file}'"
        docker run --rm --network=haitaton-backend_backbone -v $(pwd)/tormays_polys:/tormays_polys osgeo/gdal:alpine-small-latest \
            ogr2ogr -overwrite -f PostgreSQL PG:"dbname='$DB_NAME' host='$DB_HOST' port='$DB_PORT' user='$DB_USER' password='$DB_PASS'" \
            ${POLYS_DIR}/${file}
        echo "Imported '${file}'"
    else
        echo "[$counter/${#FILES[@]}] Skipping, '${file}' does not exist."
    fi
done

end=`date +%s`
runtime=$((end-start))
rt=$(echo "scale=2; $runtime / 60" | bc -l)
echo "Import took $rt minutes"
