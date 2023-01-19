TRUNCATE TABLE
    applications,
    audit_logs,
    geometriat,
    hanke,
    hankealue,
    hankegeometria,
    hanketyomaatyyppi,
    hankeyhteystieto,
    int_lock,
    organisaatio,
    permissions,
    tormaystarkastelutulos;
UPDATE allu_status SET history_last_updated = '2017-01-01T00:00:00Z';
