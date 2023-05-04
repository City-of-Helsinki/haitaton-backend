TRUNCATE TABLE
    application_attachment,
    applications,
    audit_logs,
    geometriat,
    hanke,
    hanke_attachment,
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
