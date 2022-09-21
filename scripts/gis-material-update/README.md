# Intro

Download and process Haitaton spatial material (laastariaineisto).

# Architecture, general description

Data is fetched from data sources and processed accordingly with a custom built containers.

## haitaton-gis-fetch

Downloads the requested data.

## haitaton-gis-process

Process data. Actual processing requirements are data dependent.

## database

Local development database is set up with PostGIS spatial support.

## Volume mappings

Local directory `haitaton-downloads` is mapped to fetch and processing containers.

External volume `haitaton_gis_prepare` contains scripts for processing.

External volume `haitaton_gis_db` is dedicated for database.

## Volumes

TODO: verify that *haitaton_gis_prepare* is created during file copy

External volumes are set up
* haitaton_gis_prepare
* haitaton_gis_db

Initialize volumes:

```
docker volume create --name=haitaton_gis_prepare
docker volume create --name=haitaton_gis_db
```
N.b. haitaton_gis_prepare volume mapping is automatically generated durin data copying script use.

Removal of external volumes (destructive):
```
docker volume rm haitaton_gis_prepare
docker volume rm haitaton_gis_db
```

Local directory bind mount is visible to data fetch and data processing containers:
* ./haitaton-downloads

## Build images

```
docker-compose build
```

Will build _fetch_ and _process_ images. Note, that extra step is needed
to copy actual script files to external volume.

## Copy script files to external volume

Script files are copied to external disk using:

```
sh copy-files.sh
```

After active development phase it might be more practical to handle file
copying in `Dockerfile`s and avoid explicit copying.

## Running data fetch

```
docker-compose run --rm gis-fetch <source_1> ... <source_N>
```
Where `<source>` is currently one of:
* `hsl`
* `osm`
* `ylre_katualue`
* `ylre_katuosat`
* `maka_autoliikennemaarat`

Data files are downloaded to ./haitaton-downloads -directory.

## Processing

Data processing interface is similar to data fetch.

```
docker-compose run --rm gis-process <source>
```

Actual processing functionality will be added later.

# Maintenance process

In order to maintain current process, or support new materials, following
steps need to be taken.

## Adding new data source

Basic principle is the following:

* add relevant configuration section to configuration YAML file
* implement download support to data fetch script
* implement support in processing script
* build containers (if required)
* copy files to external volume
* run fetch and process

### Adding support to data fetch

* Edit data fetch script in `fetch` directory.
* Copy edited script to external volume
* run data fetch via `docker compose`
* verify that files are downloaded to shared directory

### Adding support to data processing

* edit processing script in `process` directory
* support processing of new data source
    * add new processing class
    * implement necessary functionality
    * add produced GIS material to database
