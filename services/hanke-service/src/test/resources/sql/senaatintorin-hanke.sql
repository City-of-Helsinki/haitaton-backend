-- Create a hanke with a hankealue that covers the Senaatintori square
INSERT INTO hanke (id,
                   hanketunnus,
                   nimi,
                   kuvaus,
                   alkupvm,
                   loppupvm,
                   vaihe,
                   onykthanke,
                   version,
                   createdbyuserid,
                   createdat,
                   modifiedbyuserid,
                   modifiedat,
                   tyomaakatuosoite,
                   status)
VALUES (5,
        'HAI23-5',
        'Neliöhanke',
        'Neliöhanke',
        '2024-11-18',
        '2024-11-24',
        'OHJELMOINTI',
        false,
        1,
        '5296012a-117d-11ed-96cc-0a580a820245',
        '2023-03-07 16:24:01.562893',
        '5296012a-117d-11ed-96cc-0a580a820245',
        '2023-03-07 16:24:20.847656',
        'Senaatintori',
        'DRAFT');

INSERT INTO geometriat(id, version, createdbyuserid, createdat)
VALUES (15, 1, '5296012a-117d-11ed-96cc-0a580a820245', '2023-03-07 16:24:20.842');

INSERT INTO hankegeometria (id, hankegeometriatid, parametrit, geometria)
VALUES (25,
        15,
        '{
          "hankeTunnus": "HAI23-5"
        }',
        ST_GeomFromGeoJSON('{"type":"Polygon","crs":{"type":"name","properties":{"name":"EPSG:3879"}},"coordinates":[[[25497412.79,6673005.42],[25497281.43,6672999.77],[25497286.55,6672914.96],[25497418.13,6672920.2],[25497412.79,6673005.42]]]}'));

INSERT INTO hankealue(id,
                      haittaalkupvm,
                      haittaloppupvm,
                      kaistahaitta,
                      kaistapituushaitta,
                      meluhaitta,
                      polyhaitta,
                      tarinahaitta,
                      geometriat,
                      hankeid)
VALUES (23,
        '2024-11-18',
        '2024-11-24',
        0,
        0,
        0,
        0,
        0,
        15,
        5);
