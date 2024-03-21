--liquibase formatted sql
--changeset Topias Heinonen:072-add-on-delete-cascade-to-hakemus-yhteystieto-and-yhteyshenkilo
--comment: Add ON DELETE CASCADE to foreign keys in hakemusyhteystieto and hakemusyhteyshenkilo.

ALTER TABLE hakemusyhteystieto
    DROP CONSTRAINT fk_hakemusyhteystieto_applications,
    ADD CONSTRAINT fk_hakemusyhteystieto_applications
        FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE;

ALTER TABLE hakemusyhteyshenkilo
    DROP CONSTRAINT fk_hakemusyhteyshenkilo_hakemusyhteystieto,
    DROP CONSTRAINT fk_hakemusyhteyshenkilo_hankekayttaja,
    ADD CONSTRAINT fk_hakemusyhteyshenkilo_hakemusyhteystieto
        FOREIGN KEY (hakemusyhteystieto_id) REFERENCES hakemusyhteystieto (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_hakemusyhteyshenkilo_hankekayttaja
        FOREIGN KEY (hankekayttaja_id) REFERENCES hankekayttaja (id) ON DELETE CASCADE;
