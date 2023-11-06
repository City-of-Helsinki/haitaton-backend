#!/bin/sh

# Start Azurite in the background
azurite-blob --blobHost 0.0.0.0 --blobPort 10000 --location /data --debug /workspace/azurite-debug.log &

# Wait for Azurite to be ready
./wait-for.sh localhost:10000 --timeout=60 -- echo "Azurite is up"

# Create the containers
node create-containers.js

# Keep the script running to prevent the container from exiting
tail -f /dev/null
