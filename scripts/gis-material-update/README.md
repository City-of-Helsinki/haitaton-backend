# Intro

How to download and process Haitaton spatial material (laastariaineisto)

# Architecture, general description

Data is fetched from data sources with a custom built container.

Local development database is set up.

Processing is done with a custom built container. Results are saved to disk
and also to local development database.

## Volumes

External volumes are set up
* haitaton_gis_prepare
* haitaton_gis_db

Initialize volumes:
```
docker volume create --name=haitaton_gis_prepare
docker volume create --name=haitaton_gis_db
```

Local directory bind mount is visible to data fetch and data processing containers:
* ./haitaton-downloads

## Running data fetch

```
docker-compose run --rm gis-fetch <source_1> ... <source_N>
```
Where `<source>` is one of:
* `hsl`
* `osm`
* `ylre_katualue`
* `ylre_katuosat`
* `maka_autoliikennemaarat`

Data is downloaded to ./haitaton-downloads -directory

## Processing

```
docker-compose run --rm gis-process <source>
```

# Maintenance process

## Adding new data source

* add relevant configuration section to configuration YAML file
* implement download support to data fetch script
* implement support in processing script

### Adding support to data fetch

_to be added_

### Adding support to data processing

_to be added_

