-- Create a hanke with several hankealue some of which cover the Senaatintori square
INSERT INTO hanke (id,
                   hanketunnus,
                   nimi,
                   kuvaus,
                   vaihe,
                   onykthanke,
                   version,
                   createdbyuserid,
                   createdat,
                   modifiedbyuserid,
                   modifiedat,
                   tyomaakatuosoite,
                   status,
                   generated)
VALUES (5,
        'HAI23-5',
        'Neliöhanke',
        'Neliöhanke',
        'OHJELMOINTI',
        false,
        1,
        '5296012a-117d-11ed-96cc-0a580a820245',
        '2023-03-07 16:24:01.562893',
        '5296012a-117d-11ed-96cc-0a580a820245',
        '2023-03-07 16:24:20.847656',
        'Senaatintori',
        'DRAFT',
        false);

INSERT INTO geometriat(id, version, createdbyuserid, createdat)
VALUES (15, 1, '5296012a-117d-11ed-96cc-0a580a820245', '2023-03-07 16:24:20.842');

-- Geometry from senaatintori.json
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
                      hankeid,
                      nimi)
VALUES (23,
        '2024-11-18',
        '2024-11-24',
        0,
        0,
        0,
        0,
        0,
        15,
        5,
        'Senaatintori');

INSERT INTO geometriat(id, version, createdbyuserid, createdat)
VALUES (16, 1, '5296012a-117d-11ed-96cc-0a580a820245', '2023-03-07 16:24:20.842');

-- Geometry from aleksanterin-patsas.json, inside senaatintori.json
INSERT INTO hankegeometria (id, hankegeometriatid, parametrit, geometria)
VALUES (26,
        16,
        '{
          "hankeTunnus": "HAI23-5"
        }',
        ST_GeomFromGeoJSON('{"type":"Polygon","crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG::3879"}},"coordinates":[[[25497359.518652536,6672969.5525672035],[25497339.943859655,6672968.432023809],[25497341.06440305,6672948.857230929],[25497360.63919593,6672949.977774323],[25497359.518652536,6672969.5525672035]]]}'));

INSERT INTO hankealue(id,
                      haittaalkupvm,
                      haittaloppupvm,
                      kaistahaitta,
                      kaistapituushaitta,
                      meluhaitta,
                      polyhaitta,
                      tarinahaitta,
                      geometriat,
                      hankeid,
                      nimi)
VALUES (24,
        '2024-12-18',
        '2024-12-24',
        0,
        0,
        0,
        0,
        0,
        16,
        5,
        'Aleksanterin patsas');


INSERT INTO geometriat(id, version, createdbyuserid, createdat)
VALUES (17, 1, '5296012a-117d-11ed-96cc-0a580a820245', '2023-03-07 16:24:20.842');

-- Geometry from havis-amanda.json, outside senaatintori.json
INSERT INTO hankegeometria (id, hankegeometriatid, parametrit, geometria)
VALUES (27,
        17,
        '{
          "hankeTunnus": "HAI23-5"
        }',
        ST_GeomFromGeoJSON('{"type":"Polygon","crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG::3879"}},"coordinates":[[[25497310.408341587,6672755.371221568],[25497292.327081736,6672753.394765861],[25497294.303537443,6672735.313506011],[25497312.384797294,6672737.289961718],[25497310.408341587,6672755.371221568]]]}'));

INSERT INTO hankealue(id,
                      haittaalkupvm,
                      haittaloppupvm,
                      kaistahaitta,
                      kaistapituushaitta,
                      meluhaitta,
                      polyhaitta,
                      tarinahaitta,
                      geometriat,
                      hankeid,
                      nimi)
VALUES (27,
        '2024-12-18',
        '2024-12-24',
        0,
        0,
        0,
        0,
        0,
        17,
        5,
        'Havis Amanda');

INSERT INTO geometriat(id, version, createdbyuserid, createdat)
VALUES (18, 1, '5296012a-117d-11ed-96cc-0a580a820245', '2023-03-07 16:24:20.842');

-- Geometry from tuomiokirkon-portaat.json, partially outside senaatintori.json
INSERT INTO hankegeometria (id, hankegeometriatid, parametrit, geometria)
VALUES (28,
        18,
        '{
          "hankeTunnus": "HAI23-5"
        }',
        ST_GeomFromGeoJSON('{"type":"Polygon","crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG::3879"}},"coordinates":[[[25497382.07123757,6673019.67695647],[25497310.565242782,6673017.308837434],[25497311.48615779,6672996.875358528],[25497382.65218655,6672999.631020797],[25497382.07123757,6673019.67695647]]]}'));

INSERT INTO hankealue(id,
                      haittaalkupvm,
                      haittaloppupvm,
                      kaistahaitta,
                      kaistapituushaitta,
                      meluhaitta,
                      polyhaitta,
                      tarinahaitta,
                      geometriat,
                      hankeid,
                      nimi)
VALUES (28,
        '2024-12-18',
        '2024-12-24',
        0,
        0,
        0,
        0,
        0,
        18,
        5,
        'Tuomiokirkon portaat');
