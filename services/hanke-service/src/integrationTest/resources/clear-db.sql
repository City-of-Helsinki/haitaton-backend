TRUNCATE TABLE
    application_attachment,
    application_attachment_content,
    applications,
    audit_logs,
    geometriat,
    hanke,
    hanke_attachment,
    hankekayttaja,
    hankealue,
    hankegeometria,
    hanketyomaatyyppi,
    hankeyhteyshenkilo,
    hankeyhteystieto,
    int_lock,
    kayttajakutsu,
    permissions,
    tormaystarkastelutulos;
UPDATE allu_status SET history_last_updated = '2017-01-01T00:00:00Z';
