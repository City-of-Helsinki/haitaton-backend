-- Create a hanke with no hankealue
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
                   status,
                   generated)
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
        'DRAFT',
        false);