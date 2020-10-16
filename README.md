# Haitaton 2.0
Haitaton 2.0 backend project.

## Requirements

TODO: IntelliJ Idea, with the initialized project dependencies, has things built-in or fetches almost
everything automatically, except separate OpenJDK (for manual running). But would be nice to test
and document what is needed for a fully manual command line build from these sources. (And needed for
CI, too).

TODO: document versions (once we settle with them).

Using Idea:
* IntelliJ Idea
   * it contains its own JDK, kotlinc, gradle, etc.
* OpenJDK (version 11+) - for running things after they have been built

Manual build
* OpenJDK (version 11+)
* Kotlin compiler
* Gradle

## Modules

* services/hanke-service
    * Haitaton 2.0 Hanke service

See README.md in each individual module. It is not meant to be built *everything* from the root but build each service on its own.
But, it is still possible to build everything by running
```
$ ./gradlew build
```
in root directory.