#!/bin/sh

for o in $*
do
    /opt/venv/bin/python /haitaton-gis/process_data.py $o
done
