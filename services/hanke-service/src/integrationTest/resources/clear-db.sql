TRUNCATE TABLE
    application_attachment,
    applications,
    audit_logs,
    geometriat,
    hakemusyhteyshenkilo,
    hakemusyhteystieto,
    hanke,
    hanke_attachment,
    hankealue,
    hankegeometria,
    hankekayttaja,
    hanketyomaatyyppi,
    hankeyhteyshenkilo,
    hankeyhteystieto,
    hankkeen_haittojenhallintasuunnitelma,
    int_lock,
    kayttajakutsu,
    paatos,
    permissions,
    tormaystarkastelutulos,
    ui_notification_banner,
    valmistumisilmoitus;
UPDATE allu_status SET history_last_updated = '2017-01-01T00:00:00Z';
