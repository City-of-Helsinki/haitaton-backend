#!/bin/sh

# Print update commands to stdout

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

# All tormays-polygon files
FILES=$(parse_config "files")

# file count for loop reference
# This approach does not support file names with spaces
file_count=$(echo $FILES | wc -w)

counter=0
for file in ${FILES}; do
    counter=$((counter + 1))
    layer=${file%.gpkg}
    echo "# [$counter/$file_count] Updating: $trg for file: '$file', layer: '$layer'"
    echo 'ogr2ogr -overwrite -f PostgreSQL PG:"host=$HAITATON_HOST dbname=$HAITATON_DATABASE user=$HAITATON_USER password=$HAITATON_PASSWORD sslmode=require" /vsicurl/'$FILESHARE/$file $layer
    echo ""
done
