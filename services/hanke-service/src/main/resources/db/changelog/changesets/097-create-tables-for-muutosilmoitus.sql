--liquibase formatted sql
--changeset Topias Heinonen:097-create-tables-for-muutosilmoitus
--comment: Create tables for muutosilmoitus and yhteystiedot

CREATE TABLE muutosilmoitus
(
    id               uuid                     NOT NULL PRIMARY KEY,
    application_id   bigint                   NOT NULL,
    sent             timestamptz                       DEFAULT NULL,
    application_data jsonb                    NOT NULL,
    created_at       timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp with time zone          DEFAULT NULL,
    CONSTRAINT fk_muutosilmoitus_applications FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT uk_muutosilmoitus_application_id UNIQUE (application_id)
);

CREATE INDEX idx_muutosilmoitus_application_id ON muutosilmoitus (application_id);

CREATE TRIGGER muutosilmoitus_updated
    BEFORE UPDATE
    ON muutosilmoitus
    FOR EACH ROW
EXECUTE PROCEDURE set_updated();

COMMENT ON TABLE muutosilmoitus is 'A request to change a made decision. Data is initially copied from the application. When the user is happy with their changes, the changed data will be sent to Allu. After a new decision has been made, the data will replace the main application data.';
COMMENT ON COLUMN muutosilmoitus.application_id is 'The application this is a response to.';
COMMENT ON COLUMN muutosilmoitus.application_data is 'The application data, including the changes done here.';
COMMENT ON COLUMN muutosilmoitus.created_at is 'Timestamp of when this row was initially created.';
COMMENT ON COLUMN muutosilmoitus.updated_at is 'Timestamp of when this row was last changed.';

CREATE TABLE muutosilmoituksen_yhteystieto
(
    id                uuid                     NOT NULL PRIMARY KEY,
    muutosilmoitus_id uuid                     NOT NULL,
    tyyppi            varchar                  NOT NULL,
    rooli             varchar                  NOT NULL,
    nimi              text                     NOT NULL,
    sahkoposti        text,
    puhelinnumero     text,
    registry_key      varchar,
    created_at        timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp with time zone          DEFAULT NULL,
    CONSTRAINT fk_muutosilmoituksen_yhteystieto_muutosilmoitus FOREIGN KEY (muutosilmoitus_id) REFERENCES muutosilmoitus (id) ON DELETE CASCADE,
    CONSTRAINT uk_muutosilmoituksen_yhteystieto_rooli UNIQUE (muutosilmoitus_id, rooli)
);

CREATE INDEX idx_muutosilmoituksen_yhteystieto_muutosilmoitus_id ON muutosilmoituksen_yhteystieto (muutosilmoitus_id);

CREATE TRIGGER muutosilmoituksen_yhteystieto_updated
    BEFORE UPDATE
    ON muutosilmoituksen_yhteystieto
    FOR EACH ROW
EXECUTE PROCEDURE set_updated();

COMMENT ON TABLE muutosilmoituksen_yhteystieto is 'Customers of a muutosilmoitus. Initially copied from the application.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.id is 'A new unique ID for this row.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.muutosilmoitus_id is 'The muutosilmoitus this customer belongs to.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.tyyppi is 'Whether this customer is a company, natural person or something else.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.rooli is 'The role this customer has in the work applied for.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.nimi is 'The name of the person or organization.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.sahkoposti is 'The email for the customer.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.puhelinnumero is 'The phone number for the customer.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.registry_key is 'The personal identity code (henkilötunnus) or business ID (y-tunnus) or some other unique key for the customer. Only needed for some roles in some application types.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.created_at is 'Timestamp of when this row was initially created.';
COMMENT ON COLUMN muutosilmoituksen_yhteystieto.updated_at is 'Timestamp of when this row was last changed.';

CREATE TABLE muutosilmoituksen_yhteyshenkilo
(
    id                               uuid                     NOT NULL PRIMARY KEY,
    muutosilmoituksen_yhteystieto_id uuid                     NOT NULL,
    hankekayttaja_id                 uuid                     NOT NULL,
    tilaaja                          boolean,
    created_at                       timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                       timestamp with time zone          DEFAULT NULL,
    CONSTRAINT fk_muutosilmoituksen_yhteyshenkilo_yhteystieto FOREIGN KEY (muutosilmoituksen_yhteystieto_id) REFERENCES muutosilmoituksen_yhteystieto (id) ON DELETE CASCADE,
    CONSTRAINT fk_muutosilmoituksen_yhteyshenkilo_hankekayttaja FOREIGN KEY (hankekayttaja_id) REFERENCES hankekayttaja (id) ON DELETE CASCADE
);

CREATE INDEX idx_muutosilmoituksen_yhteyshenkilo_yhteystieto_id ON muutosilmoituksen_yhteyshenkilo (muutosilmoituksen_yhteystieto_id);

CREATE TRIGGER muutosilmoituksen_yhteyshenkilo_updated
    BEFORE UPDATE
    ON muutosilmoituksen_yhteyshenkilo
    FOR EACH ROW
EXECUTE PROCEDURE set_updated();

COMMENT ON TABLE muutosilmoituksen_yhteyshenkilo is 'Contacts of a muutosilmoitus. Initially copied from the application.';
COMMENT ON COLUMN muutosilmoituksen_yhteyshenkilo.id is 'A new unique ID for this row.';
COMMENT ON COLUMN muutosilmoituksen_yhteyshenkilo.muutosilmoituksen_yhteystieto_id is 'The customer this is a contact for.';
COMMENT ON COLUMN muutosilmoituksen_yhteyshenkilo.hankekayttaja_id is 'The user in the project who is the contact.';
COMMENT ON COLUMN muutosilmoituksen_yhteyshenkilo.tilaaja is 'Whether this user is the one who sent the application.';
COMMENT ON COLUMN muutosilmoituksen_yhteyshenkilo.created_at is 'Timestamp of when this row was initially created.';
COMMENT ON COLUMN muutosilmoituksen_yhteyshenkilo.updated_at is 'Timestamp of when this row was last changed.';
