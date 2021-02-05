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

## Authentication
JWT/OIDC-based authentication using Keycloak and Spring Security. 

### Testing with curl 

Retrieve access_token (auth-service needs to on, replace `USERNAME` and `PASSWORD` with real values - create new user in Keycloak if necessary):
```shell
curl -XPOST 'http://localhost:3030/auth/realms/haitaton/protocol/openid-connect/token' \
-H 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'username=USERNAME' \
--data-urlencode 'password=PASSWORD' \
--data-urlencode 'scope=hanke-service' \
--data-urlencode 'client_id=hanke-service'
```
Answer:
```json
{"access_token":"eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJvZzhvWXZUSVBVTWVycnpaeGlpa0JTU2tBTlQwc01PYVJTc05YWlFpT09vIn0.eyJleHAiOjE2MTI1NTA2MDcsImlhdCI6MTYxMjUyMTgwNywianRpIjoiMDJjOWM4NTktZGUyZS00NDU4LWFjMDAtZjhhNjI5ZjExNTI2IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDozMDAxL2F1dGgvcmVhbG1zL2hhaXRhdG9uIiwiYXVkIjoiaGFua2Utc2VydmljZSIsInN1YiI6ImMwOTI3MmIxLTIyMzItNGU0Mi1hZjI3LWEwZWE0ODJmNTVmMCIsInR5cCI6IkJlYXJlciIsImF6cCI6ImhhbmtlLXNlcnZpY2UiLCJzZXNzaW9uX3N0YXRlIjoiZWQ5MzI3MWUtNzBlNS00ZmNmLWJiODktNWRhN2NiMjNjMTMyIiwiYWNyIjoiMSIsInNjb3BlIjoiaGFua2Utc2VydmljZSIsInVzZXJfbmFtZSI6ImMwOTI3MmIxLTIyMzItNGU0Mi1hZjI3LWEwZWE0ODJmNTVmMCIsImF1dGhvcml0aWVzIjpbIlJPTEVfaGFpdGF0b24tdXNlciJdfQ.fJSk_kqjxoteOiiB3nqQc-sGqZgKQQpBU45erzWZLS7-eKqErRS2ijNXyH5w5snkyyo4VaTBarraYBnuqpMTY8j7VutUlov7ULrSRZMV2uhhGy2IHe_l1hGq1eIA3tkG8iAxvLxgzkZqUlaSwYEYSNVXvT6doOqATqE69oItBcuJnUX7dBkX6Cv9sE0e3n9l1b5YOBX0JZ10DwPFSgGNbDDI1zhpz5t2s1dL2Bh9RsCKUn_u9kEtMUrQ9EjBXygkuUlSU7EaNXNDOjBIWRUUE4lJ-_fqeM9VflO_fxun8wpYGhJ5KrPVPc9e9sT_58Myz-8x4hYVpTH5PgF5AZY8tB","expires_in":28799,"refresh_expires_in":1799,"refresh_token":"eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJiMDRkMzY4MS1kZDY5LTRhYzktYTJlNC05MGM0MjhjYzQyMjIifQ.eyJleHAiOjE2MTI1MjM2MDcsImlhdCI6MTYxMjUyMTgwNywianRpIjoiZmZhNmRkZGYtMzNhNy00YjhkLWEzZDQtZTMxNzIyZDMzMjNjIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDozMDAxL2F1dGgvcmVhbG1zL2hhaXRhdG9uIiwiYXVkIjoiaHR0cDovL2xvY2FsaG9zdDozMDAxL2F1dGgvcmVhbG1zL2hhaXRhdG9uIiwic3ViIjoiYzA5MjcyYjEtMjIzMi00ZTQyLWFmMjctYTBlYTQ4MmY1NWYwIiwidHlwIjoiUmVmcmVzaCIsImF6cCI6ImhhbmtlLXNlcnZpY2UiLCJzZXNzaW9uX3N0YXRlIjoiZWQ5MzI3MWUtNzBlNS00ZmNmLWJiODktNWRhN2NiMjNjMTMyIiwic2NvcGUiOiJoYW5rZS1zZXJ2aWNlIn0.0ZtMSAqr8xXES8o1rnt4lWKw8LtU4CbcUz01EWcg83M","token_type":"Bearer","not-before-policy":0,"session_state":"ed93271e-70e5-4fcf-bb89-5da7cb23c132","scope":"hanke-service"}
```

Inside access_token there is expected to be following (use e.g. https://jwt.io/ to decode the token):
```json
{
  "exp": 1612550607,
  "iat": 1612521807,
  "jti": "02c9c859-de2e-4458-ac00-f8a629f11523",
  "iss": "http://localhost:3001/auth/realms/haitaton",
  "aud": "hanke-service",
  "sub": "c09272b1-2232-4e42-af27-a0ea482f55f0",
  "typ": "Bearer",
  "azp": "hanke-service",
  "session_state": "ed93271e-70e5-4fcf-bb89-5da7cb23c132",
  "acr": "1",
  "scope": "hanke-service",
  "user_name": "c09272b1-2232-4e42-af27-a0ea482f55f0",
  "authorities": [
    "ROLE_haitaton-user"
  ]
}
```
* `user_name` is used by Spring Security automatically (in order to this to work there has to be a Mapper inside Keycloak 'hanke-service' Client Scope - it is pre-configured in local auth-service)
* `authorities` are mapped as roles by Spring Security (in order to this to work there has to be a Mapper inside Keycloak 'hanke-service' Client Scope - it is pre-configured in local auth-service)
* `aud` is used by Spring Security as resource id (in order to this to work there has to be a Mapper inside Keycloak 'hanke-service' Client Scope - it is pre-configured in local auth-service)

Call a service (replace `ACCESS_TOKEN` with access_token from previous auth request):
```shell
curl -H 'Authorization: Bearer ACCESS_TOKEN' http://localhost:8080/hankkeet/HAI21-6
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
