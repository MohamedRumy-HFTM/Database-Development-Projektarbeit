-- Standorte, an denen Fahrzeuge stationiert sind.
CREATE TABLE standort (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    ort  VARCHAR(60) NOT NULL,
    CONSTRAINT uq_standort_name UNIQUE (name)
);
