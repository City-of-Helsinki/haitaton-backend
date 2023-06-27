# Haitaton 2.0
Haitaton 2.0 project API for "hanke" data.

## Requirements

Using IDEA:
* IntelliJ IDEA
   * it contains its own JDK, gradle, etc.
* OpenJDK (version 17+) - for running things after they have been built
* Docker

Manual build
* OpenJDK (version 17+)
* Docker

## How to compile, build and run

IntelliJ IDEA will basically automatically fetch dependencies, compile, and build
most of this stuff with default settings as soon as it sees the relevant gradle stuff and correct module setting.\
However, that will only run the tests under "test"; manual build is needed for integration tests, see below.

Building the service with both unit and integration tests:
```
$ ./gradlew :services:hanke-service:clean :services:hanke-service:build :services:hanke-service:integrationTest
```
Starting the application/services can be done afterwards with command line at haitaton-backend root directory:
```
$ ./gradlew :services:hanke-service:bootRun
```

After the application has started, the services should be available at URLs (see Swagger UI (below) 
for full API description) (and other sub-URLs similarly):
> http://localhost:8080/hankkeet/<id> \
> http://localhost:8080/hankkeet/<id>/geometriat

### Git hooks

A pre-push git hook for running all checks is created when building the project
with Gradle. The hooks can also be added or updated manually with the command:
```
$ ./gradlew installGitHooks
```

This adds a hook that will build the project and run all tests and other checks
before any push you make. The checks need to be run successfully for the push to
happen. If necessary, the checks can be skipped with `git push --no-verify`.

### Swagger UI

Swagger UI (see https://springdoc.org/) and OpenAPI v3 description (JSON). Note though that the swagger
setup can not currently support authentication, so can not test the actions with it.

Locally without Docker:
- [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

When running the services with Docker Compose:
- [http://localhost:3001/api/swagger-ui/index.html](http://localhost:3001/api/swagger-ui/index.html)
- [http://localhost:3001/api/v3/api-docs](http://localhost:3001/api/v3/api-docs)

On dev:
- [https://haitaton-dev.agw.arodevtest.hel.fi/api/swagger-ui/index.html](https://haitaton-dev.agw.arodevtest.hel.fi/api/swagger-ui/index.html)
- [https://haitaton-dev.agw.arodevtest.hel.fi/api/v3/api-docs](https://haitaton-dev.agw.arodevtest.hel.fi/api/v3/api-docs)

On test:
- [https://haitaton-test.agw.arodevtest.hel.fi/api/swagger-ui/index.html](https://haitaton-test.agw.arodevtest.hel.fi/api/swagger-ui/index.html)
- [https://haitaton-test.agw.arodevtest.hel.fi/api/v3/api-docs](https://haitaton-test.agw.arodevtest.hel.fi/api/v3/api-docs)

### Spotless formatter

The Spotless Gradle plugin checks during the build stage that all code is formatted with ktfmt. If
the code is not formatted correctly, the build will fail.

Only code changed since the origin/dev branch will be checked and formatted. When a file is touched,
the whole file needs to be reformatted, though.

The formatting can be checked with:
```
./gradlew spotlessCheck`
```
And the code can be reformatted with:
```
./gradlew spotlessApply
```

Installing the ktfmt plugin to IDEA is recommended.

If you really, really need to format something manually, you can disable Spotless for a code block:
```
// spotless: off
val table = arrayOf(
     0,  1,  2,  3,  4,  5,  6,  7,  8,  9,
    10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
    )
// spotless: on
```

## PostgreSQL + PostGis locally

The repository includes support for local development with a docker-postgres
without the need to install postgres locally.

To run the postgres in localhost
```
 cd scripts/docker-postgres
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
either set it in your own environment beforehand or just use the syntax
with env file stated as in the steps below.
  
If you need to change the build context, you can do so in .env.local file.   
  
### How to run docker-compose

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

### GIS data import
In order to run Törmäystarkastelus locally one needs to import GIS data. This can be done after docker-compose is up and running (at least `db`).
Maintenance VPN (`huolto.hel.fi`) is needed to access the files.
```shell
cd haitaton-backend
sh scripts/HEL-GIS-data-import.sh
```
or in Windows:
```shell
cd haitaton-backend
.\scripts\HEL-GIS-data-import.cmd
```

## Emails

When running locally, the system emails are sent to smtp4dev, which is started as part of the Docker
Compose setup. You can access the sent emails by opening http://localhost:3003.

## Authentication

Authentication is done by calling Helsinki Profiili's userinfo-url with the Bearer-token.

## GDPR API

GDPR API is disabled by default when running the service locally. The issuer defined by
`haitaton.gdpr.issuer` needs to be reachable for the application to start. By default, this is set
to point to a profile-gdpr-api-tester running on the same Docker network as Haitaton, but it's not
started by the Docker Compose setup.

The GDPR API can be tested by running [profile-gdpr-api-tester](https://github.com/City-of-Helsinki/profile-gdpr-api-tester).
That application calls Haitaton with an authentication token that matches the real Profile.

Clone and set up the application as specified in their documentation. 

Set the API Tester configuration as follows (in profile-gdpr-api-tester/.env):
```
  ISSUER = http://gdpr-api-tester:8888/
  GDPR_API_AUDIENCE = http://localhost:8080/haitaton
  GDPR_API_AUTHORIZATION_FIELD = http://localhost:8080
  GDPR_API_QUERY_SCOPE = haitaton.gdprquery
  GDPR_API_DELETE_SCOPE = haitaton.gdprdelete
  GDPR_API_URL = http://haitaton-hanke:8080/gdpr-api/$user_uuid
  PROFILE_ID = 65d4015d-1736-4848-9466-25d43a1fe8c7
  USER_UUID = <Your user id>
  LOA = substantial
  SID = 00000000-0000-4000-9000-000000000001
```
You can find your user id from the database:
```postgresql
select createdbyuserid from hanke;
-- Or
select userid from applications;
```

To make the API tester work with Haitaton running in Docker Compose, start it like this: 
```shell
docker run --rm -i -p 8888:8888 --network haitaton-backend_backbone --name gdpr-api-tester --env-file .env gdpr-api-tester
```

After the API tester has started, GDPR API can be enabled in Haitaton. Add
```
HAITATON_GDPR_DISABLED=false
```
to Haitaton environment (haitaton-backend/.env). Restart haitaton-hanke service inside the Docker
Compose setup. Rebuilding is not necessary.

You should now be able to call GDPR API with the API tester's `query`, `delete` and `delete dryrun`
commands.

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
