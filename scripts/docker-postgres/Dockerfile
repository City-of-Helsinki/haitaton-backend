# Based on <https://registry.hub.docker.com/r/postgis/postgis>

FROM postgis/postgis:11-3.0

COPY ./configure-database.sh /docker-entrypoint-initdb.d/
COPY ./configure-postgres.sh /docker-entrypoint-initdb.d/