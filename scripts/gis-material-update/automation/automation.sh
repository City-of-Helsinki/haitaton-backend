#!/bin/sh

# Collect the sources needed for all targets:
sources=""
for tormays_source in $*
do
    case $tormays_source in
    hsl)
        sources="${sources} hsl hki"
        ;;
    tram_infra)
        sources="${sources} hki osm helsinki_osm_lines"
        ;;
    tram_lines)
        sources="${sources} hki hsl"
        ;;
    liikennevaylat)
        sources="${sources} liikennevaylat central_business_area ylre_katuosat"
        ;;
    *)
        sources="${sources} $tormays_source"
        ;;
    esac
done
# De-duplicate the sources
sources=$(echo "$sources" | xargs -n1 | cat -n | sort -k2 | uniq -f1 | sort -k1 | cut -f2- |  xargs)
# Fetch the sources
echo "Fetching $* ..."
sh /haitaton-gis/fetch_all.sh ${sources}
RESULT1="$?"
if [ "$RESULT1" != "0" ]; then
    echo -e "Fetching exit Code:" $RESULT1"\nFAILED to fetch data."
    exit 0
else
    for tormays_source in $*
    do
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
        RESULT2="$?"
        if [ "$RESULT2" != "0" ]; then
            echo -e "Processing exit Code:" $RESULT1"\nFAILED to process data."
        else
            # Validate and deploy data
            echo "Validating and deploing $tormays_source ..."
            /opt/venv/bin/python /haitaton-gis-validate-deploy/validate_deploy_data.py $tormays_source
        fi
    done
fi
