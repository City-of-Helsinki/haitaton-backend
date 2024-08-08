--liquibase formatted sql
--changeset Topias Heinonen:080-create-ui-banners-table
--comment: Add an index to paatos, to make finding decisions by application identifier efficient.

CREATE TABLE ui_notification_banner_types
(
    id varchar(255) PRIMARY KEY NOT NULL
);

INSERT INTO ui_notification_banner_types
VALUES ('INFO'),
       ('WARNING'),
       ('ERROR');

CREATE TABLE ui_notification_banner
(
    id       varchar(255) PRIMARY KEY NOT NULL, -- INFO, WARNING or ERROR
    text_fi  TEXT                     NOT NULL CHECK (text_fi <> ''),
    text_sv  TEXT                     NOT NULL CHECK (text_sv <> ''),
    text_en  TEXT                     NOT NULL CHECK (text_en <> ''),
    label_fi TEXT                     NOT NULL CHECK (label_fi <> ''),
    label_sv TEXT                     NOT NULL CHECK (label_sv <> ''),
    label_en TEXT                     NOT NULL CHECK (label_en <> ''),
    FOREIGN KEY (id) references ui_notification_banner_types (id)
);

COMMENT ON TABLE ui_notification_banner_types IS 'Enum types for different kinds of notifications.';
COMMENT ON COLUMN ui_notification_banner_types.id is 'An allowed type for notifications.';

COMMENT ON TABLE ui_notification_banner IS 'Notifications that will be displayed in the UI. Uses more checking than most tables, because the values are meant to be edited directly to the DB for now.';
COMMENT ON COLUMN ui_notification_banner.id IS 'The type of notification this is. Only one notification per type is allowed.';
COMMENT ON COLUMN ui_notification_banner.text_fi IS 'Finnish text body';
COMMENT ON COLUMN ui_notification_banner.text_sv IS 'Swedish text body';
COMMENT ON COLUMN ui_notification_banner.text_en IS 'English text body';
COMMENT ON COLUMN ui_notification_banner.label_fi IS 'Finnish label text';
COMMENT ON COLUMN ui_notification_banner.label_sv IS 'Swedish label text';
COMMENT ON COLUMN ui_notification_banner.label_en IS 'English label text';
