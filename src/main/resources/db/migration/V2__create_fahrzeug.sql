-- Fahrzeuge der Flotte. km_stand ist bewusst redundant (aus der letzten Fahrt ableitbar)
-- und wird beim Fahrtabschluss in derselben Transaktion nachgeführt.
-- version dient dem optimistischen Locking (JPA @Version).
CREATE TABLE fahrzeug (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kennzeichen VARCHAR(10) NOT NULL,
    modell      VARCHAR(60) NOT NULL,
    kategorie   VARCHAR(12) NOT NULL,
    km_stand    INTEGER     NOT NULL DEFAULT 0,
    in_betrieb  BOOLEAN     NOT NULL DEFAULT TRUE,
    standort_id BIGINT      NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_fahrzeug_kennzeichen UNIQUE (kennzeichen),
    CONSTRAINT ck_fahrzeug_kennzeichen CHECK (kennzeichen ~ '^[A-Z]{2} [0-9]{1,6}$'),
    CONSTRAINT ck_fahrzeug_kategorie CHECK (kategorie IN ('KLEIN', 'KOMBI', 'TRANSPORTER')),
    CONSTRAINT ck_fahrzeug_km_stand CHECK (km_stand >= 0),
    CONSTRAINT fk_fahrzeug_standort FOREIGN KEY (standort_id) REFERENCES standort (id)
);
