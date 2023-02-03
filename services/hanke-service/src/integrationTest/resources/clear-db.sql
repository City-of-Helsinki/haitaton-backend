TRUNCATE TABLE
    applications,
    audit_logs,
    geometriat,
    attachment,
    hanke,
    hanke_kayttaja,
    hankealue,
    hankegeometria,
    hanketyomaatyyppi,
    hankeyhteystieto,
    int_lock,
    kayttaja_tunniste,
    organisaatio,
    permissions,
    tormaystarkastelutulos;
UPDATE allu_status SET history_last_updated = '2017-01-01T00:00:00Z';
