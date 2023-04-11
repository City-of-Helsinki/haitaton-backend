# Contents

This directory is for saving external source files,
e.g. GIS materials provided via email.

# Usage

Ideally, there would be no need to copy files into this directory, but all source
materials should be available via API or via download from public sources.

Materials that are not yet available via public sources, are listed in this document.

## Cycling infra

Current implementation assumes cycling infra source file in this directory.

Copy [source file](https://helsinginkaupunki.sharepoint.com/:u:/r/sites/KYMPHaitaton/Jaetut%20asiakirjat/General/02_Taustamateriaalit/Laastariaineisto/haitaton_2_input/helsinki_cycleways.gpkg?csf=1&web=1&e=pR3rqm)
from Sharepoint to this directory.

### Instructions to generate combined file

If (for any reason) there is a need to generate combined cycling infra file,
it can be done using following procedure.

Cycling infra source materials are available:

- [Main routes](https://helsinginkaupunki.sharepoint.com/:u:/r/sites/KYMPHaitaton/Jaetut%20asiakirjat/General/02_Taustamateriaalit/Laastariaineisto/haitaton2spatial/input/Helsinki_pp_paareitit.zip?csf=1&web=1&e=gT8SUz)
- [prioritized routes](https://helsinginkaupunki.sharepoint.com/:u:/r/sites/KYMPHaitaton/Jaetut%20asiakirjat/General/02_Taustamateriaalit/Laastariaineisto/haitaton2spatial/input/priorisoidut_reitit.zip?csf=1&web=1&e=75maG1)

Extract zipped shapefiles.

Create one cycling output file combining both infra data:

```sh
$ ogr2ogr -nln pp -nlt MULTILINESTRING \
    -f GPKG helsinki_cycleways.gpkg \
    -sql "SELECT 'main' as type FROM Helsinki_pp_paareitit" Helsinki_pp_paareitit Helsinki_pp_paareitit
$ ogr2ogr -nln pp -nlt MULTILINESTRING \
    -f GPKG helsinki_cycleways.gpkg \
    -sql "SELECT 'prio' as type FROM priorisoidut_reitit" -append priorisoidut_reitit priorisoidut_reitit
```
