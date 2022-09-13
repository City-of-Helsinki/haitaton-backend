#!/bin/sh

for o in $*
do
    sh /haitaton-gis/fetch_data.sh $o
done
