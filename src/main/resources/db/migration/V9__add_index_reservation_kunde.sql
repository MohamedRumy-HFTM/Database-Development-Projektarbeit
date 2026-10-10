-- A4 (T10): Reservationen eines Kunden, neueste zuerst, seitenweise.
-- Ohne diesen Index liest PostgreSQL die ganze Tabelle (Seq Scan) und sortiert danach.
-- Die Spalten entsprechen Filter und Sortierung der Abfrage: kunde_id (=), dann beginn DESC, id DESC.
-- So liest PostgreSQL die ersten Einträge direkt in der richtigen Reihenfolge und hört danach auf.
-- Nebeneffekt: Der Fremdschlüssel fk_reservation_kunde ist damit ebenfalls indexiert.
-- Messung vorher/nachher: scripts/messung-a4.sql, Ergebnisse in docs/performance.md.
CREATE INDEX ix_reservation_kunde_beginn ON reservation (kunde_id, beginn DESC, id DESC);
