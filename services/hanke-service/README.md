# Haitaton 2.0
Haitaton 2.0 project API for "hanke" data.

## Requirements
TODO: IntelliJ IDEA, with the initialized project dependencies, has things built-in or fetches almost
everything automatically, except separate OpenJDK (for manual running). But would be nice to test
and document what is needed for a fully manual command line build from these sources. (And needed for
CI, too).

TODO: document versions (once we settle with them).

Using IDEA:
* IntelliJ IDEA
   * it contains its own JDK, gradle, etc.
* OpenJDK (version 11+) - for running things after they have been built

Manual build
* OpenJDK (version 11+)
* Gradle

## How to compile, build and run
TODO: Seems IntelliJ IDEA will basically automatically fetch dependencies, compile, and build
most of this stuff with default settings as soon as it sees the relevant gradle stuff and correct module setting.\
However, there are some steps left (runtime stuff?)..\
Also, do check what are the manual steps needed to do the same?

Starting the application/services can be done afterwards with command line at haitaton-backend root directory:
```
$ ./gradlew :services:hanke-service:bootRun
```

After the application has started, the services should be available at URLs:
> http://localhost:8080/ \
> http://localhost:8080/api/hello/

Swagger UI (see https://springdoc.org/) and OpenAPI v3 description (JSON):
> http://localhost:8080/swagger-ui.html \
> http://localhost:8080/v3/api-docs

At least Firefox seems to be able to show the REST JSON results in a nice way directly.

## PostgreSQL + PostGis locally
The repository includes support for local development with a docker-postgres
 without the need to install postgres locally. 

To run the postgres in localhost
```
 cd scripts/postgres-docker
./build-postgres-docker.sh 
```
You can change the port and data folder to your liking in configure-database
.sh and build-postgres-docker.sh. Note that you will also need to change
 settings in application.properties accordingly.
 
## Setting up build process

...
Note that task 'integrationTest' is not run automatically as part of the Gradle build-task.
It has been added to the .github/xxx.yml file to be part of CI process.


## History
Project was initialized with [spring initializr](https://start.spring.io/), and the result added
on top of the pre-created project stub at Github (which contained only the license and short readme.md).
