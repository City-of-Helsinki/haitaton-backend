# Haitaton 2.0 Authentication Service

This service contains only a Dockerfile for building a Haitaton `auth-service` image instead of using a plain Keycloak image.

See local `docker-compose.yml` (in root) and also `docker-compose.yml` in `haitaton-deploy` project for information how this image is used in local development and testing.

Service is running directly at http://localhost:3030/auth/ or if `haitaton-nginx` is running also http://localhost:8080/auth/

Username and password are `admin`:`admin` 

## 'haitaton' Keycloak realm
For local development (`/docker-compose.yml`) there is a realm json file `haitaton-realm-with-users.json` that included both the realm settings and the user settings.

Currently there are these users:
* `admin` (password `admin`)

Groups:
* `admin` (having role `admin`)

Roles:
* `admin`

The realm file is automatically imported into Keycloak at start-up by this environment setting in docker-compose.yml:
```
      - KEYCLOAK_IMPORT=/tmp/haitaton-realm-with-users.json
```
Notice that `KEYCLOAK_IMPORT` is ignored if there already exists `haitaton` realm (see https://github.com/keycloak/keycloak-documentation/blob/master/server_admin/topics/export-import.adoc). 
In that case one has to manually delete `auth-db`'s PostgreSQL data directory (`~/haitaton-auth-data`) and restart `auth-service`

### Making changes to the realm
In order to make changes to the realm itself or the users in it one has to do following:
* make sure that `auth-service` is running
* log in Keycloak at http://localhost:3030/auth/
* make the modifications inside web console
* export the realm and users by doing this:
  * open terminal connection to the keycloak container: `docker-compose --env-file .env.local exec auth-service sh`
  * inside container run the export command:  `/opt/jboss/keycloak/bin/standalone.sh -Djboss.socket.binding.port-offset=100 -Dkeycloak.migration.action=export -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.realmName=haitaton -Dkeycloak.migration.usersExportStrategy=REALM_FILE -Dkeycloak.migration.file=/tmp/haitaton-realm-with-users.json`
    (this will update file `realms/haitaton-realm-with-users.json`)
  * AND if you made changes to the realm you need to ALSO export it without the user data (for other than local dev environments): `/opt/jboss/keycloak/bin/standalone.sh -Djboss.socket.binding.port-offset=100 -Dkeycloak.migration.action=export -Dkeycloak.migration.provider=dir -Dkeycloak.migration.realmName=haitaton -Dkeycloak.migration.usersExportStrategy=SAME_FILE -Dkeycloak.migration.dir=/tmp`
    (this will update files `realms/haitaton-realm.json` and `realms/haitaton-users-0.json`)
* after export you need to stop `auth-service` container and manually delete `auth-db`´s PostgreSQL data directory (`~/haitaton-auth-data`)
* PS. if export commands create some new directories under `realms` please add them to `.gitignore` - they are not needed but in some cases JBoss (Keycloak) may create them


TODO: desdribe how this image should be used in OpenShift environment (pass all the environment variables needed).
