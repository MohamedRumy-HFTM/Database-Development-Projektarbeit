-- Abholstandort: Standort des Fahrzeugs zum Zeitpunkt der Buchung.
-- Damit bleiben Auswertungen je Standort korrekt, wenn ein Fahrzeug später verschoben wird.
ALTER TABLE reservation ADD COLUMN standort_id BIGINT;

-- Bestehende Reservationen erhalten den aktuellen Standort ihres Fahrzeugs.
UPDATE reservation r
SET standort_id = f.standort_id
FROM fahrzeug f
WHERE f.id = r.fahrzeug_id;

ALTER TABLE reservation
    ALTER COLUMN standort_id SET NOT NULL,
    ADD CONSTRAINT fk_reservation_standort FOREIGN KEY (standort_id) REFERENCES standort (id);
