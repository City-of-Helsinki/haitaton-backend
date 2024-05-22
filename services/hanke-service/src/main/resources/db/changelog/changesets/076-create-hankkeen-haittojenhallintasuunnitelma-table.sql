--liquibase formatted sql
--changeset Teemu Hiltunen:076-create-hankkeen-haittojenhallintasuunnitelma-table
--comment: Create a table for project's nuisance management plan

CREATE TABLE hankkeen_haittojenhallintasuunnitelma
(
    hankealue_id        bigint                   NOT NULL,
    tyyppi              varchar(255)             NOT NULL,
    sisalto             text                     NOT NULL,
    CONSTRAINT fk_hanke
        FOREIGN KEY (hankealue_id)
            REFERENCES hankealue (id) ON DELETE CASCADE
);

CREATE INDEX idx_hankealue ON hankkeen_haittojenhallintasuunnitelma (hankealue_id);

COMMENT ON TABLE hankkeen_haittojenhallintasuunnitelma IS 'Table for project nuisance management plan.';
COMMENT ON COLUMN hankkeen_haittojenhallintasuunnitelma.hankealue_id IS 'The project area of which nuisance management plan this is.';
COMMENT ON COLUMN hankkeen_haittojenhallintasuunnitelma.tyyppi IS 'Type of nuisance.';
COMMENT ON COLUMN hankkeen_haittojenhallintasuunnitelma.sisalto IS 'Content of the nuisance management plan.';
