#!/bin/sh

# Script to import downloaded GIS material to local development database

set -e

config_yaml="./hel-gis-data-variables.yaml"

. ./parse_config_common.sh

usage () {
    echo "Usage: $0 <source directory>"
    exit 2
}

if [ $# -ne 1 ] || ! [ -d "$1" ]; then
    echo "Source directory must exist"
    usage
else
    source_directory=$1
fi

DB_NAME="haitaton"
DB_USER="haitaton_user"
DB_PASS="haitaton"
DB_HOST="db"
DB_PORT="5432"

# All tormays-polygon files
FILES=$(parse_config "files")

if [ "$FILES" = "" ]; then
    echo "Files not found from configuration file: '$config_yaml'"
    exit 3
fi

# file count for loop reference
# This approach does not support file names with spaces
file_count=$(echo $FILES | wc -w)

start=`date +%s`

counter=0
for file in $FILES; do
    counter=$((counter + 1))
    if [ -f "${source_directory}/${file}" ]; then
        echo "[$counter/${file_count}] Importing '${file}'"
        docker run --rm --network=haitaton-backend_backbone \
            -v $(pwd)/tormays_polys:/tormays_polys osgeo/gdal:alpine-small-latest \
            ogr2ogr -overwrite -f PostgreSQL \
                PG:"dbname='$DB_NAME' host='$DB_HOST' port='$DB_PORT' user='$DB_USER' password='$DB_PASS'" \
                ${source_directory}/${file}
        echo "Imported '${file}'"
    else
        echo "[$counter/${file_count}] Skipping, '${source_directory}/${file}' does not exist."
    fi
done

end=`date +%s`
runtime=$((end-start))
rt=$(echo "scale=2; $runtime / 60" | bc -l)
echo "Import took $rt minutes"
