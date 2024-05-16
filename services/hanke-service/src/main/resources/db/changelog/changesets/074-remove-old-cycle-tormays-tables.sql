--liquibase formatted sql
--changeset Topias Heinonen:074-remove-old-cycle-tormays-tables
--comment: Remove the old cycle tormays tables.

DROP TABLE IF EXISTS tormays_cycleways_basic_polys;
DROP TABLE IF EXISTS tormays_cycleways_main_polys;
DROP TABLE IF EXISTS tormays_cycleways_priority_polys;
