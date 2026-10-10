-- btree_gist erlaubt im Exclusion-Constraint den Gleichheitsvergleich auf fahrzeug_id.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Reservationen. Ein Zeitraum ist halboffen [beginn, ende): eine Reservation bis 14 Uhr
-- und eine ab 14 Uhr überschneiden sich nicht.
CREATE TABLE reservation (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kunde_id    BIGINT      NOT NULL,
    fahrzeug_id BIGINT      NOT NULL,
    beginn      TIMESTAMPTZ NOT NULL,
    ende        TIMESTAMPTZ NOT NULL,
    status      VARCHAR(13) NOT NULL DEFAULT 'AKTIV',
    erstellt_am TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reservation_kunde FOREIGN KEY (kunde_id) REFERENCES kunde (id),
    CONSTRAINT fk_reservation_fahrzeug FOREIGN KEY (fahrzeug_id) REFERENCES fahrzeug (id),
    CONSTRAINT ck_reservation_zeitraum CHECK (ende > beginn),
    CONSTRAINT ck_reservation_status CHECK (status IN ('AKTIV', 'STORNIERT', 'ABGESCHLOSSEN')),
    -- Zwei nicht stornierte Reservationen desselben Fahrzeugs dürfen sich nie überschneiden,
    -- auch nicht bei gleichzeitigen Transaktionen.
    CONSTRAINT ex_reservation_ueberschneidung EXCLUDE USING gist (
        fahrzeug_id WITH =,
        tstzrange(beginn, ende, '[)') WITH &&
    ) WHERE (status <> 'STORNIERT')
);
