-- Eine Fahrt entsteht erst beim Abschluss einer Reservation (1:0..1).
-- Als eigene Tabelle gibt es in reservation keine leeren Rückgabe-Spalten.
CREATE TABLE fahrt (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT      NOT NULL,
    km_start       INTEGER     NOT NULL,
    km_ende        INTEGER     NOT NULL,
    rueckgabe_am   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_fahrt_reservation UNIQUE (reservation_id),
    CONSTRAINT fk_fahrt_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT ck_fahrt_km_start CHECK (km_start >= 0),
    CONSTRAINT ck_fahrt_km CHECK (km_ende >= km_start)
);
