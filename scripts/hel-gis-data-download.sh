#!/bin/sh

# Script to download tormays polygons GIS material from project fileshare
# Maintenance VPN is needed to access the fileshare.

set -e

config_yaml="./hel-gis-data-variables.yaml"

supported_environments="dev|test"

usage () {
    echo "Usage: $0 <$supported_environments>"
    exit 2
}

. ./parse_config_common.sh

if [ $# -ne 1 ]; then
    usage
else
    target=$1
fi

# ensure lower case
trg=$(echo $target | tr '[:upper:]' '[:lower:]')

if ! $(echo "$supported_environments" | tr "|" " " | grep -q "\b${trg}\b");
then
    usage
fi

# Read fileshare address from configuration file
FILESHARE=$(parse_config "environment.$trg.fileshare")

if [ "$FILESHARE" = "" ]; then
    echo "Fileshare URL for '$trg' not found from configuration file: '$config_yaml'"
    exit 3
fi

POLYS_DIR=$(parse_config polys_dir)

if ! [ -d "$POLYS_DIR" ]; then
    echo "Directory '$POLYS_DIR' does not exist, creating."
    mkdir "$POLYS_DIR"
fi

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
    echo "[$counter/$file_count] Downloading ${file} to $POLYS_DIR"
    wget --show-progress "${FILESHARE}/${file}" -O "${POLYS_DIR}/${file}"
    echo "Downloaded ${file}"
done

end=`date +%s`
runtime=$((end-start))
rt=$(echo "scale=2; $runtime / 60" | bc -l)
echo "Download took $rt minutes"
echo "Files are downloaded to directory: ${POLYS_DIR}"
