# Intro

Automation module for Haitaton spatial material.

# Quickstart

In order to get started, following steps need to be taken.
- Fill out variables in file `haitaton.env`
- Check configuration file `gis-material-update/config.yaml`. At least `tormays_table_org`, `validate_limit_min` and `validate_limit_max` need to be set right. For others default values are ok.
- Build `haitaton-gis-automation` image
- Run `haitaton-gis-automation` container

## Build images
Fill out following variables in `gis-material-update/automation/haitaton.env`:

```
HAITATON_USER=
HAITATON_PASSWORD=
HAITATON_HOST=
HAITATON_PORT=
HAITATON_DATABASE=
HELSINKI_EXTRANET_USERNAME=
HELSINKI_EXTRANET_PASSWORD=
```
These values are for target database where tormays material will be put and if `liikennevaylat` source is used then also two HELSINKI_EXTRANET_ variables are needed.

Give following in folder `/automation`:
```sh
docker build -t haitaton-gis-automation -f ./Dockerfile ..
```
This wil build `haitaton-gis-automation` image.

## Run image
Give following:
```sh
docker run --rm --network host --env-file haitaton.env haitaton-gis-automation <source_1> ... <source_N>
```

Where `<source>` is currently one of:

- `hsl` - HSL bus routes
- `tram_lines` - Tram routes.
- `tram_infra` - Tram infra.
- `ylre_katualueet` - Helsinki YLRE street areas, polygons.
- `ylre_katuosat` - Helsinki YLRE parts, polygons.
- `maka_autoliikennemaarat` - Traffic volumes (car traffic)
- ~~`cycle_infra` - Cycle infra (local file)~~
- `central_business_area` - Helsinki city "kantakaupunki"
- `liikennevaylat` - Helsinki city street classes

Source `cycle_infra` is not yet supported because it is using static local files.

This docker image run will fetch `<source>` data, process it and after that `<source>` data will be validated and deployed to the database using given environment variables.