-- Eine Zeile pro abgeschlossener Fahrt. Die View legt zentral fest, was als
-- abgeschlossene Fahrt zählt und wie Nutzungsdauer und Kilometer berechnet werden.
CREATE VIEW v_fahrt_nutzung AS
SELECT r.id                                                     AS reservation_id,
       r.fahrzeug_id,
       r.standort_id,
       r.beginn,
       f.rueckgabe_am,
       EXTRACT(EPOCH FROM (f.rueckgabe_am - r.beginn)) / 3600.0 AS nutzung_h,
       f.km_ende - f.km_start                                   AS km
FROM reservation r
JOIN fahrt f ON f.reservation_id = r.id
WHERE r.status = 'ABGESCHLOSSEN';
