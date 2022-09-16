docker container create --name dummy -v haitaton_gis_prepare:/haitaton-gis ubuntu

docker container run --rm -it --volumes-from dummy ubuntu /bin/bash

docker rm dummy