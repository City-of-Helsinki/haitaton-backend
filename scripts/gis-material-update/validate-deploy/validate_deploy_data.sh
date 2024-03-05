#!/bin/sh

for o in $*
do
    /opt/venv/bin/python /haitaton-gis-validate-deploy/validate_deploy_data.py $o
done
