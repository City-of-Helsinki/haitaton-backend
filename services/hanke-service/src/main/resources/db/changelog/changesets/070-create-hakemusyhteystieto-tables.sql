--liquibase formatted sql
--changeset Topias Heinonen:070-create-hakemusyhteystieto-tables
--comment: Create tables for storing the customers of an application and their contacts outside the application data JSON object.

create table hakemusyhteystieto
(
    id             uuid    not null primary key,
    application_id bigint  not null,
    tyyppi         varchar not null,
    rooli          varchar not null,
    nimi           text    not null,
    sahkoposti     text,
    puhelinnumero  text,
    y_tunnus       varchar,
    constraint fk_hakemusyhteystieto_applications foreign key (application_id) references applications (id),
    constraint uk_hakemusyhteystieto_application_rooli unique (application_id, rooli)
);
create index idx_application_id on hakemusyhteystieto (application_id);

COMMENT ON TABLE hakemusyhteystieto IS 'The customers of the hakemus.';
COMMENT ON COLUMN hakemusyhteystieto.application_id IS 'The application this customer is defined for.';
COMMENT ON COLUMN hakemusyhteystieto.tyyppi IS 'Whether this customer is a natural person or a company or an association.';
COMMENT ON COLUMN hakemusyhteystieto.rooli IS 'Whether this customer is the customer, contractor, representative or property developer.';
COMMENT ON COLUMN hakemusyhteystieto.nimi IS 'Name of the organization or full name of the person.';
COMMENT ON COLUMN hakemusyhteystieto.sahkoposti IS 'Email for the customer.';
COMMENT ON COLUMN hakemusyhteystieto.puhelinnumero IS 'Telephone number for the customer.';
COMMENT ON COLUMN hakemusyhteystieto.y_tunnus IS 'Business ID of the customer if the customer is an organization.';

create table hakemusyhteyshenkilo
(
    id                    uuid    not null primary key,
    hakemusyhteystieto_id uuid    not null,
    hankekayttaja_id      uuid    not null,
    tilaaja               boolean not null,
    constraint fk_hakemusyhteyshenkilo_hakemusyhteystieto foreign key (hakemusyhteystieto_id) references hakemusyhteystieto (id)
);
create index idx_hankekayttaja_id on hakemusyhteyshenkilo (hankekayttaja_id);
create index idx_hakemusyhteystieto_id on hakemusyhteyshenkilo (hakemusyhteystieto_id);

COMMENT ON TABLE hakemusyhteyshenkilo IS 'Contact person for a customer. Links hankekayttajat with hakemus customers.';
COMMENT ON COLUMN hakemusyhteyshenkilo.hakemusyhteystieto_id IS 'The customer the person is a contact for.';
COMMENT ON COLUMN hakemusyhteyshenkilo.hankekayttaja_id IS 'The hankekayttaja who is the contact person.';
COMMENT ON COLUMN hakemusyhteyshenkilo.tilaaja IS 'Whether is contact is the one who ordered the hakemus.';
