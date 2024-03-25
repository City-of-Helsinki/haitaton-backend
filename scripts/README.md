# New repository

The folder `gis-material-update` is moved to on own repository `https://github.com/City-of-Helsinki/haitaton-gis-update`
and instructions can be found in `https://github.com/City-of-Helsinki/haitaton-gis-update/blob/main/README.md´

Older instruction can be still found below.

# Updating tormays -material to environemnts

General overview:

These instructions will guide through various steps. Actual steps
taken is dependent on the task at hand.

- upload generated polygons to Haitaton file share (for others to access)
- download polygons from file share to local disk (if needed)
- update GIS materials from local disk to local dev database
- update to dev
- update to test

Support for staging and production environments can be easily added later on, if needed.

Prerequisites:

- tormays polygon gis material is already generated

## Prerequisite: Generate tormays polygons

Generating tormays-polygon data is instructed in `gis-material-update/README.md`

## Prerequisite: copy relevant addresses to `hel-gis-data-variables.yaml`

File `hel-gis-data-variables.yaml.example` should be copied to name `hel-gis-data-variables.yaml`,
and correct addresses to dev and test environment file shares should be edited to yaml file.

File share addresses are documented in project instructions in Confluence.

## Upload generated polygons to Haitaton file share

Script run will resolve fileshare name and detect processed materials from
processing output directory (`gis-material-update/haitaton-gis-output`).

Commands are printed to stdout during script run, if processed material is found from directory.

Following steps will provide commands to run:

- ensure VPN is active and you are logged to OpenShift
- select correct project, as instructed in oc login `oc project <projectname>`
- run `sh hel-gis-print-upload-commands.sh`
- copy and paste selected commands to run
- alternatively, if all commands seem to be fine to run, you can pipe result to `sh`
  to be executed in the following manner: `sh hel-gis-print-upload-commands.sh | sh`

## Download from file share to local disk

Following command will download files from file share.

Output directory and tormays polygon files are configured in separate configuration file.

Following steps will download files to local directory:

- ensure VPN is active
- run `sh hel-gis-data-download.sh <dev|test>`

## Update material from local disk to local development database

Local directory and tormays polygon files are configured in separate configuration file.

- ensure VPN is passive (VPN blocks local port usage for some deployments)
- run `sh hel-gis-data-import-to-db.sh <directory>`

## Update material to dev or test

Process:

- run locally `sh hel-gis-print-update-commands.sh <dev|test>`
- ensure VPN is active
- start oc debug session in gdal container `oc debug deployment/gdal`
- copy and paste selected update commands to gdal prompt

### Check database contents

Database GIS content can be easily verified by following steps.

Start OpenShift debug session in gdal pod

```sh
$ oc debug deployment/gdal
```

And check for the database GIS contents:

```sh
$ ogrinfo PG:"host=$HAITATON_HOST dbname=$HAITATON_DATABASE user=$HAITATON_USER password=$HAITATON_PASSWORD sslmode=require"
```

Note that value expansion for variables is done in gdal pod.

# Proposed interactive workflow

- download and process gis material (as per instructions)
- test locally
  - update to local dev database
  - inspect table contents
- upload to fileshare
  - print upload commands (using provided script)
- update to dev (or test)
  - print update commands (using provided script)
  - start gdal pod
  - update selected materials
