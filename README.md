# Haitaton 2.0

Haitaton is a service owned by the city of Helsinki that supports the management and prediction of the adverse
effects of projects taking place within the urban area.

## Requirements

* OpenJDK (version 17+)
* Docker
* [Docker-compose](https://docs.docker.com/compose/install/)
* IntelliJ IDEA is recommended for development purposes

## How to compile, build and run

Building the service with both unit and integration tests:

```
$ ./gradlew :services:hanke-service:clean :services:hanke-service:check
```

Starting the application/services can be done afterwards with command line at haitaton-backend root directory:

```
$ ./gradlew :services:hanke-service:bootRun
```

### Docker compose

Required directory structure:

``` 
  ├── haitaton
  │   ├── haitaton-backend
  │   └── haitaton-ui
```

#### Usage examples

- `docker-compose up -d`
    - Run the entire stack, -d can be omitted
- `docker-compose up db haitaton-hanke`
    - Run individual services
- `docker-compose stop haitaton-ui && docker-compose up -d --build --no-deps haitaton-ui`
    - Rebuild and run an individual service
- `docker-compose down`
    - Stop containers and removes containers networks volumes and images created by up

See docker-compose.yml for details.

#### Azurite

For emulating Azure Blob Storage, an Azurite (see https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite)
instance is started with Docker Compose. Blob Containers are also created via Docker Compose:
- `haitaton-hakemusliitteet-local`
- `haitaton-hankeliitteet-local`
- `haitaton-paatokset-local`

Notice that Azurite uses the default well-known development `AccountName` and `AccountKey`:
```shell
AccountName: devstoreaccount1
AccountKey: Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==
```

Azurite is accessible via Microsoft Azure Storage Explorer (see https://azure.microsoft.com/en-us/products/storage/storage-explorer/)
or Azure CLI (see https://learn.microsoft.com/en-us/cli/azure/).
For example, to list all blob containers with Azure CLI:
```shell
$ az storage container list --connection-string "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;"
```

Azurite is configured to produce debug logging in `azurite-debug.log`.
Azurite uses Docker top-level volume for data (see https://docs.docker.com/compose/compose-file/07-volumes/).

Connecting to Azurite for hanke-service is handled with connection strings. For `./gradlew bootRun`,
the connection string is set in build.gradle.kts. The special development storage string can be used
there. For the Docker Compose environment, the connection string is set in docker-compose.yml. It's
slightly more involved, since it needs a customized endpoint to connect inside the Docker network.

Connection strings are not set in the cloud environments. In cloud environments, the connection is
authenticated by the default authenticator, which reads environment variables AZURE_CLIENT_ID,
AZURE_CLIENT_SECRET and AZURE_TENANT_ID to build an authenticator.

### Swagger UI

Swagger UI (see https://springdoc.org/) and OpenAPI v3 description (JSON). You
can use the Swagger UI to send requests, if you copy your bearer token over from
the browser. So,

1. Log in to Haitaton.
2. Open the Network tab from developer tools.
3. Open e.g. Omat Hankkeet in Haitaton.
4. From the backend request, copy the content of the Authorization header, that
   comes after the Bearer keyword.
5. In the Swagger UI of the same environment, open the Authorize dialog.
6. Paste the bearer token.
7. Send a request as a logged-in user.

Authentication for the GDPR API is different from the other application, and
it's not configured for the Swagger UI. GDPR API can be tested using the
specialized tester, as detailed in [GDPR API section](#gdpr-api).

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

## Tools

### Git hooks

A pre-push git hook for running all checks is created when building the project
with Gradle. The hooks can also be added or updated manually with the command:

```
$ ./gradlew installGitHooks
```

This adds a hook that will build the project and run all tests and other checks
before any push you make. The checks need to be run successfully for the push to
happen. If necessary, the checks can be skipped with `git push --no-verify`.

Custom pre-push scripts can be added under `.git/hooks/pre-push.d`. Push will
fail if any of the pre-push scripts fail.

### Code coverage report

Gradle Jacoco plugin is used to create a code coverage report.

```
$ ./gradlew :services:hanke-service:test :services:hanke-service:integrationTest :services:hanke-service:jacocoTestReport
```

Created report can be found at paths:

- Html: build/reports/jacoco/test/html/index.html
- Xml: build/reports/jacoco/test/jacocoTestReport.xml

### Spotless formatter

The Spotless Gradle plugin checks during the build stage that all code is formatted with ktfmt. If
the code is not formatted correctly, the build will fail.

Only code changed since the origin/dev branch will be checked and formatted. When a file is touched,
the whole file needs to be reformatted, though.

The formatting can be checked with:

```
./gradlew spotlessCheck
```

And the code can be reformatted with:

```
./gradlew spotlessApply
```

Installing the ktfmt plugin to IDEA is recommended.

### Sentry

Sentry error tracking is integrated via the Spring Boot starter. It can be enabled or disabled with
environment variables without changing the code.

Key variables:

* `HAITATON_SENTRY_DSN` – Overrides the default DSN defined in `application.yml`.
* `HAITATON_SENTRY_ENVIRONMENT` – Controls activation. If set to `none` (default), Sentry SDK is
  disabled (no events or breadcrumbs are sent). Any other value both enables Sentry and is used as
  the Sentry environment tag (e.g. `dev`, `test`, `prod`).
* `HAITATON_SENTRY_LOGGING_ENABLED` – When true, Logback events at or above the configured level are
  sent as Sentry breadcrumbs/events (see `sentry.logging.minimum-event-level`).

Examples:

Disable completely (local default):
```shell
export HAITATON_SENTRY_ENVIRONMENT=none
```

Enable in a dev shell:
```shell
export HAITATON_SENTRY_ENVIRONMENT=dev
export HAITATON_SENTRY_DSN=https://<public_key>@oXXXX.ingest.sentry.io/<project_id>
```

Enable logging integration:
```shell
export HAITATON_SENTRY_LOGGING_ENABLED=true
```

The conditional logic lives in `SentryConditionalConfig`, which disables the SDK early when the
environment is `none`.

## Database

This project uses PostreSQL relational database which is extended by PostGIS. In short, PostGIS adds support for
geographic objects in the database. Database management, schema changes and data migrations are done with Liquibase. See
./services/hanke-service/src/main/resources/db/changelog/

### GIS data import

In order to run Törmäystarkastelus locally one needs to import GIS data. This can be done after docker-compose is up and
running (at least `db`).
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

Creation of new emails is done with [mjml.io](https://mjml.io/). Either IntelliJ or Visual Studio Code plugin MJML is
needed. Mjml templates are located in email/. The output email content (html) is in
hanke-service/resources/email/template.

MJML templates are automatically compiled to HTML during build. This is done using a Gradle plugin
that outputs the compiled HTML files under the build directory. A custom task (`copyEmailTemplates`)
is used to copy the compiled HTML files over to the resources-directory.

The Gradle plugin is only looking at changes in the `.mjml` files, so it doesn't do the automatic
recompiling if there are only changes to the `.partial` -files. In these cases it's necessary to
force a new compile with `--rerun-tasks`. This can be done either as a part of the general build or
as a separate step:
```shell
./gradlew copyEmailTemplates --rerun-tasks
```

## File scan

Haitaton supports uploading of attachment files. Files are validated with ClamAV anti-malware tool.

## Authentication

Authentication is handled by Spring Boot's default configuration. It defaults to the Authentication
code with PKCE flow Profiili Keycloak uses as well. The access token's signature, issuer and
audience are checked and the subject is read from the token.

## GDPR API

GDPR API is disabled by default when running the service locally. The issuer defined by
`haitaton.gdpr.issuer` needs to be reachable for the application to start. By default, this is set
to point to a profile-gdpr-api-tester running on the same Docker network as Haitaton, but it's not
started by the Docker Compose setup.

The GDPR API can be tested by
running [profile-gdpr-api-tester](https://github.com/City-of-Helsinki/profile-gdpr-api-tester).
That application calls Haitaton with an authentication token that matches the real Profile.

Clone and set up the application as specified in their documentation.

Set the API Tester configuration as follows (in profile-gdpr-api-tester/.env):

```
  ISSUER = http://gdpr-api-tester:8888/
  ISSUER_TYPE = keycloak
  GDPR_API_AUDIENCE = haitaton-api-dev
  GDPR_API_AUTHORIZATION_FIELD = http://localhost:8080
  GDPR_API_QUERY_SCOPE = gdprquery
  GDPR_API_DELETE_SCOPE = gdprdelete
  GDPR_API_URL = http://haitaton-hanke:8080/gdpr-api/$user_uuid
  PROFILE_ID = 65d4015d-1736-4848-9466-25d43a1fe8c7
  USER_UUID = <Your user id>
  LOA = substantial
  SID = 00000000-0000-4000-9000-000000000001
```

You can find your user id from the database:

```postgresql
select createdbyuserid
from hanke;
-- Or
select userid
from applications;
```

Build the tester tool with:
```shell
docker build -t gdpr-api-tester .
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
