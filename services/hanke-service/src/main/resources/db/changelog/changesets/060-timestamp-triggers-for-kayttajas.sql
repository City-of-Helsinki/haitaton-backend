CREATE OR REPLACE FUNCTION set_updated()
    RETURNS TRIGGER AS
$$
BEGIN
    -- Only set updated_at on an actual change
    IF row (NEW.*) IS DISTINCT FROM row (OLD.*) THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
        RETURN NEW;
    ELSE
        RETURN OLD;
    END IF;
END;
$$ language 'plpgsql';

CREATE TRIGGER hankekayttaja_updated
    BEFORE UPDATE
    ON hankekayttaja
    FOR EACH ROW
EXECUTE PROCEDURE set_updated();

CREATE TRIGGER kayttajakutsu_updated
    BEFORE UPDATE
    ON kayttajakutsu
    FOR EACH ROW
EXECUTE PROCEDURE set_updated();
