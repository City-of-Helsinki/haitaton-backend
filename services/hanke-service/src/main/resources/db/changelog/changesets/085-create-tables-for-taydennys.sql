--liquibase formatted sql
--changeset Topias Heinonen:085-create-tables-for-taydennys
--comment: Create tables for taydennys, add index for taydennyspyynto

CREATE INDEX idx_taydennyspyynto_application_id ON taydennyspyynto (application_id);
CREATE INDEX idx_taydennyspyynnon_kentta_taydennyspyynto_id ON taydennyspyynnon_kentta (taydennyspyynto_id);

CREATE TABLE taydennys
(
    id                 uuid                     NOT NULL PRIMARY KEY,
    taydennyspyynto_id uuid                     NOT NULL,
    application_data   jsonb                    NOT NULL,
    created_at         timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamp with time zone          DEFAULT NULL,
    CONSTRAINT fk_taydennys_taydennyspyynto FOREIGN KEY (taydennyspyynto_id) REFERENCES taydennyspyynto (id) ON DELETE CASCADE,
    CONSTRAINT uk_taydennys_taydennyspyynto_id UNIQUE (taydennyspyynto_id)
);

CREATE TRIGGER taydennys_updated
    BEFORE UPDATE
    ON taydennys
    FOR EACH ROW
EXECUTE PROCEDURE set_updated();

CREATE INDEX idx_taydennys_taydennyspyynto_id ON taydennys (taydennyspyynto_id);

COMMENT ON TABLE taydennys is 'A response to an information request (täydennyspyyntö). Data is initially copied from the application. When the user is happy with their changes, the changed data will be sent to Allu. After sending, the data will replace the main application data.';
COMMENT ON COLUMN taydennys.taydennyspyynto_id is 'The information request (täydennyspyyntö) this is a response to.';
COMMENT ON COLUMN taydennys.application_data is 'The application data, including the changes done here.';
COMMENT ON COLUMN taydennys.created_at is 'Timestamp of when this row was initially created.';
COMMENT ON COLUMN taydennys.updated_at is 'Timestamp of when this row was last changed.';

CREATE TABLE taydennysyhteystieto
(
    id            uuid                     NOT NULL PRIMARY KEY,
    taydennys_id  uuid                     NOT NULL,
    tyyppi        varchar                  NOT NULL,
    rooli         varchar                  NOT NULL,
    nimi          text                     NOT NULL,
    sahkoposti    text,
    puhelinnumero text,
    registry_key  varchar,
    created_at    timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp with time zone          DEFAULT NULL,
    CONSTRAINT fk_taydennysyhteystieto_taydennys FOREIGN KEY (taydennys_id) REFERENCES taydennys (id) ON DELETE CASCADE,
    CONSTRAINT uk_taydennys_rooli UNIQUE (taydennys_id, rooli)
);

CREATE TRIGGER taydennysyhteystieto_updated
    BEFORE UPDATE
    ON taydennysyhteystieto
    FOR EACH ROW
EXECUTE PROCEDURE set_updated();

CREATE INDEX idx_taydennysyhteystieto_taydennys_id ON taydennysyhteystieto (taydennys_id);

COMMENT ON TABLE taydennysyhteystieto is 'Customers of a täydennys. Initially copied from the application.';
COMMENT ON COLUMN taydennysyhteystieto.id is 'A new unique ID for this row.';
COMMENT ON COLUMN taydennysyhteystieto.taydennys_id is 'The täydennys this customer belongs to.';
COMMENT ON COLUMN taydennysyhteystieto.tyyppi is 'Whether this customer is a company, natural person or something else.';
COMMENT ON COLUMN taydennysyhteystieto.rooli is 'The role this customer has in the work applied for.';
COMMENT ON COLUMN taydennysyhteystieto.nimi is 'The name of the person or organization.';
COMMENT ON COLUMN taydennysyhteystieto.sahkoposti is 'The email for the customer.';
COMMENT ON COLUMN taydennysyhteystieto.puhelinnumero is 'The phone number for the customer.';
COMMENT ON COLUMN taydennysyhteystieto.registry_key is 'The personal identity code (henkilötunnus) or business ID (y-tunnus) or some other unique key for the customer. Only needed for some roles in some application types.';
COMMENT ON COLUMN taydennysyhteystieto.created_at is 'Timestamp of when this row was initially created.';
COMMENT ON COLUMN taydennysyhteystieto.updated_at is 'Timestamp of when this row was last changed.';

CREATE TABLE taydennysyhteyshenkilo
(
    id                      uuid                     NOT NULL PRIMARY KEY,
    taydennysyhteystieto_id uuid                     NOT NULL,
    hankekayttaja_id        uuid                     NOT NULL,
    tilaaja                 boolean,
    created_at              timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              timestamp with time zone          DEFAULT NULL,
    CONSTRAINT fk_taydennysyhteyshenkilo_taydennysyhteystieto FOREIGN KEY (taydennysyhteystieto_id) REFERENCES taydennysyhteystieto (id) ON DELETE CASCADE,
    CONSTRAINT fk_taydennysyhteyshenkilo_hankekayttaja FOREIGN KEY (hankekayttaja_id) REFERENCES hankekayttaja (id) ON DELETE CASCADE
);

CREATE TRIGGER taydennysyhteyshenkilo_updated
    BEFORE UPDATE
    ON taydennysyhteyshenkilo
    FOR EACH ROW
EXECUTE PROCEDURE set_updated();

CREATE INDEX idx_taydennysyhteyshenkilo_taydennysyhteystieto_id ON taydennysyhteyshenkilo (taydennysyhteystieto_id);

COMMENT ON TABLE taydennysyhteyshenkilo is 'Contacts of a täydennys. Initially copied from the application.';
COMMENT ON COLUMN taydennysyhteyshenkilo.id is 'A new unique ID for this row.';
COMMENT ON COLUMN taydennysyhteyshenkilo.taydennysyhteystieto_id is 'The customer this is a contact for.';
COMMENT ON COLUMN taydennysyhteyshenkilo.hankekayttaja_id is 'The user in the project who is the contact.';
COMMENT ON COLUMN taydennysyhteyshenkilo.tilaaja is 'Whether this user is the one who sent the application.';
COMMENT ON COLUMN taydennysyhteyshenkilo.created_at is 'Timestamp of when this row was initially created.';
COMMENT ON COLUMN taydennysyhteyshenkilo.updated_at is 'Timestamp of when this row was last changed.';
