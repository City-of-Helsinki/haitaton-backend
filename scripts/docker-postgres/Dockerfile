# Based on <https://registry.hub.docker.com/r/postgis/postgis>

FROM postgis/postgis:13-master
RUN localedef -i fi_FI -c -f UTF-8 -A /usr/share/locale/locale.alias fi_FI.UTF-8
ENV LANG fi_FI.utf8

COPY ./configure-database.sh /docker-entrypoint-initdb.d/
COPY ./configure-postgres.sh /docker-entrypoint-initdb.d/
COPY ./laastariaineisto-test.sql /docker-entrypoint-initdb.d/
