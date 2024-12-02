--liquibase formatted sql
--changeset Topias Heinonen:083-create-tables-for-taydennyspyynto
--comment: Create tables for taydennyspyynto

CREATE TABLE taydennyspyynto
(
    id             uuid                     NOT NULL PRIMARY KEY,
    application_id bigint                   NOT NULL,
    allu_id        integer                  NOT NULL,
    created_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_taydennyspyynto_applications FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT uk_taydennyspyynto_application_id UNIQUE (application_id),
    CONSTRAINT uk_taydennyspyynto_allu_id UNIQUE (allu_id)
);

COMMENT ON TABLE taydennyspyynto IS 'Information request sent from Allu. Content consists of rows in taydennyspyynnon_kentat.';
COMMENT ON COLUMN taydennyspyynto.id IS 'The haitaton ID for this information request.';
COMMENT ON COLUMN taydennyspyynto.application_id IS 'Application this information request is directed at.';
COMMENT ON COLUMN taydennyspyynto.allu_id IS 'The ID this information request has in Allu.';
COMMENT ON COLUMN taydennyspyynto.created_at IS 'The time this object was created.';


CREATE TABLE taydennyspyynnon_kentta
(
    taydennyspyynto_id uuid                     NOT NULL,
    key                text                     NOT NULL,
    description        text                     NOT NULL,
    created_at         timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (taydennyspyynto_id, key),
    CONSTRAINT fk_taydennyspyynnon_kentat_applications FOREIGN KEY (taydennyspyynto_id) REFERENCES taydennyspyynto (id) ON DELETE CASCADE
);

COMMENT ON TABLE taydennyspyynnon_kentta IS 'A comment on a single field set in the application. The taydennyspyynto consists of a bunch of these.';
COMMENT ON COLUMN taydennyspyynnon_kentta.taydennyspyynto_id IS 'The taydennyspyynto this comment is a part of.';
COMMENT ON COLUMN taydennyspyynnon_kentta.key IS 'The field set this comment is for. One of the values in InformationRequestFieldKey in the backend.';
COMMENT ON COLUMN taydennyspyynnon_kentta.description IS 'The comment the handler in Allu has written concerning this field set.';
COMMENT ON COLUMN taydennyspyynnon_kentta.created_at IS 'The time this object was created.';
