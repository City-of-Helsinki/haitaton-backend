#!/bin/sh

# Wait for Azurite to be ready
/tmp/wait-for.sh azurite:10000 -t 30 -- echo "Azurite is up"

connection_string="\
DefaultEndpointsProtocol=http;\
AccountName=devstoreaccount1;\
AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;\
BlobEndpoint=http://azurite:10000/devstoreaccount1;\
"

# Create Blob Containers
az storage container create --name haitaton-hakemusliitteet-local --connection-string ${connection_string}
az storage container create --name haitaton-hankeliitteet-local --connection-string ${connection_string}
az storage container create --name haitaton-paatokset-local --connection-string ${connection_string}
