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

Building the service with both unit and integration tests:
```
$ ./gradlew :services:hanke-service:clean build integrationTest
```
Starting the application/services can be done afterwards with command line at haitaton-backend root directory:
```
$ ./gradlew :services:hanke-service:bootRun
```

After the application has started, the services should be available at URLs (see Swagger UI (below) for full API description):
> http://localhost:8080/hankkeet/<id> \
> http://localhost:8080/hankkeet/<id>/geometriat \
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

## Dockercompose
You can also run the whole haitaton stack with docker compose. Due to the
 fact that docker-compose wil need Dockerfiles both from backend and frontend
 , both repos need to be located in the same directory:
 
 ``` 
  ├── haitaton
  │   ├── haitaton-backend
  │   └── haitaton-ui
```

In addition, an environment file is needed to set the build root. You can
 either set it in your own environment beforehand or just use the syntaks
  with env file stated as in the steps below. 
  
If you need to change the build context, you can do so in .env.local file.   
  
### How tu run docker-compose

- Install [docker-compose](https://docs.docker.com/compose/install/)  according
 to your operating system instructions. 
- Create a directory for the project 
``` mkdir haitaton ```
- Pull both the frontend and backend project such that they are under the
 directory you just created, ie.
 ``` 
  ├── haitaton
  │   ├── haitaton-backend
  │   └── haitaton-ui
```
- Login Login to RedHat customer portal
```
docker login registry.redhat.io 
``` 
(If you do not have an account, you can create it at https://access.redhat
.com/)
- If you want to run all the services you can type
```
  docker-compose --env-file .env.local up

``` 
..or if you just want backend and db, you can do 
```
  docker-compose --env-file .env.local up db haitaton-hanke
```
..or if you just want frontend, you can do 
```
  docker-compose --env-file .env.local up haitaton-ui
```
## Info
There is a Spring Boot Actuator endpoint for general info:
> http://localhost:8081/actuator/info
```
{
    "java-name": "Java Platform API Specification",
    "java-version": "11",
    "java-vendor": "Oracle Corporation",
    "build": {
        "artifact": "hanke-service",
        "name": "hanke-service",
        "time": "2020-11-02T07:00:31.071Z",
        "version": "0.0.1-SNAPSHOT",
        "group": "fi.hel.haitaton"
    }
}
```

## Probes
There are following Spring Boot Actuator probes (https://docs.spring.io/spring-boot/docs/2.3.4.RELEASE/reference/html/production-ready-features.html#production-ready-kubernetes-probes) for Kubernetes (these must be configured in Kuberneters/OpenShift):
> http://localhost:8081/actuator/health/readiness \
> http://localhost:8081/actuator/health/liveness
 
## Setting up build process

...
Note that task 'integrationTest' is not run automatically as part of the Gradle build-task.
It has been added to the .github/xxx.yml file to be part of CI process.


## History
Project was initialized with [spring initializr](https://start.spring.io/), and the result added
on top of the pre-created project stub at Github (which contained only the license and short readme.md).
