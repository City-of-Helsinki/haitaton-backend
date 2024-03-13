#!/bin/sh

for tormays_source in $*
do
    # Fetch data
    echo "Fetching $tormays_source ..."
    case $tormays_source in
    hsl)
        sh /haitaton-gis/fetch_all.sh $tormays_source hki
        ;;
    tram_infra)
        sh /haitaton-gis/fetch_all.sh hki osm helsinki_osm_lines
        ;;
    tram_lines)
        sh /haitaton-gis/fetch_all.sh hki hsl
        ;;
    liikennevaylat)
        sh /haitaton-gis/fetch_all.sh $tormays_source central_business_area ylre_katuosat
        ;;
    *)
        sh /haitaton-gis/fetch_all.sh $tormays_source
        ;;
    esac

    # Process data
    echo "Processing $tormays_source ..."
    case $tormays_source in
    liikennevaylat)
        /opt/venv/bin/python /haitaton-gis/process_data.py central_business_area ylre_katuosat $tormays_source
        ;;
    *)
        /opt/venv/bin/python /haitaton-gis/process_data.py $tormays_source
        ;;
    esac

    # Validate and deploy data
    echo "Validating and deploing $tormays_source ..."
    /opt/venv/bin/python /haitaton-gis-validate-deploy/validate_deploy_data.py $tormays_source

done
