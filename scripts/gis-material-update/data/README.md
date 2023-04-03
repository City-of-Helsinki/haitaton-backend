# Contents

This directory is for saving external files.

## Cycling routes

In the near future, cycling routes are obtaine from API.

### File: helsinki_cycleways.gpkg

Process existing materials:

```sh
$ ogr2ogr -nln pp -nlt MULTILINESTRING \
    -f GPKG helsinki_cycleways.gpkg \
    -sql "SELECT 'main' as type FROM Helsinki_pp_paareitit" Helsinki_pp_paareitit Helsinki_pp_paareitit
$ ogr2ogr -nln pp -nlt MULTILINESTRING \
    -f GPKG helsinki_cycleways.gpkg \
    -sql "SELECT 'prio' as type FROM priorisoidut_reitit" -append priorisoidut_reitit priorisoidut_reitit
```
