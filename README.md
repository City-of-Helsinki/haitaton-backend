# Haitaton 2.0
Haitaton 2.0 backend project.

## Requirements

TODO: document versions (once we settle with them).

Using Idea:
* IntelliJ Idea
   * it contains its own JDK, kotlinc, gradle, etc.
* OpenJDK (version 17+) - for running things after they have been built

Manual build
* OpenJDK (version 17+)

## Modules

* services/auth-service
  * Haitaton 2.0 Authentication service
* services/hanke-service
  * Haitaton 2.0 Hanke service

## How to compile, build and run

See README.md in each individual module. It is not meant to be built *everything* from the root but build each service on its own.
But, it is still possible to build everything by running
```
$ ./gradlew build
```
in root directory.
