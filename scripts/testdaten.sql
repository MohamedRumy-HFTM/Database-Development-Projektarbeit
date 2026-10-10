-- Reproduzierbare Testdaten für die Performanceanalyse.
-- Voraussetzung: Schema per Flyway aufgebaut (Anwendung einmal starten).
-- Aufruf: docker compose exec -T db psql -U seeland -d seeland -v ON_ERROR_STOP=1 < scripts/testdaten.sql
--
-- Ergebnis (Stichtag 01.10.2026):
--   240 Fahrzeuge, 20'000 fiktive Kunden, rund 122'000 Reservationen von Oktober 2024 bis Anfang 2027,
--   rund 92'000 Fahrten.
--   Vor dem Stichtag: ca. 85 % abgeschlossen, 15 % storniert. Danach: ca. 85 % aktiv, 15 % storniert.
--   Standorte unterschiedlich gross und ausgelastet, Kunden stark ungleich verteilt (Vielfahrer bis Einmalnutzer).
--   Pro Fahrzeug werden die Reservationen lückenlos hintereinander erzeugt, der Exclusion-Constraint bleibt aktiv.
--
-- Wiederholbarkeit: Statt random() werden Zufallswerte per md5 aus festen Schlüsseln berechnet
-- (Kennzeichen, laufende Nummer, Zweck). Jeder Lauf erzeugt dadurch exakt dieselben Daten,
-- unabhängig vom Ausführungsplan der Datenbank.

BEGIN;

TRUNCATE fahrt, reservation, fahrzeug, kunde RESTART IDENTITY;

-- Deterministischer Zufallswert im Bereich [0, 1) für einen Schlüssel.
CREATE OR REPLACE FUNCTION pg_temp.zufall(schluessel text) RETURNS double precision
    LANGUAGE sql IMMUTABLE AS
$$
SELECT ('x' || substr(md5('seeland-mobil:' || schluessel), 1, 8))::bit(32)::bigint / 4294967296.0
$$;

-- Standorte (aus V6) mit Rang und Anzahl Reservationen pro Fahrzeug.
-- Ein höherer Wert bedeutet kürzere Lücken zwischen den Reservationen, also höhere Auslastung.
CREATE TEMP TABLE standort_profil ON COMMIT DROP AS
SELECT s.id AS standort_id,
       s.rang,
       (ARRAY [650, 550, 500, 450, 400, 380, 350, 330])[s.rang] AS reservationen_pro_fahrzeug
FROM (SELECT id, row_number() OVER (ORDER BY id)::int AS rang FROM standort) s
WHERE s.rang <= 8;

-- 240 Fahrzeuge: Biel Bahnhof 70, Biel Bözingen 40, Lyss 40, übrige je 18.
INSERT INTO fahrzeug (kennzeichen, modell, kategorie, km_stand, standort_id)
SELECT 'BE ' || (100000 + g),
       (ARRAY ['VW Polo', 'Skoda Octavia', 'Toyota Yaris', 'VW Transporter', 'Renault Zoe'])[1 + g % 5],
       CASE g % 5 WHEN 1 THEN 'KOMBI' WHEN 3 THEN 'TRANSPORTER' ELSE 'KLEIN' END,
       0,
       p.standort_id
FROM generate_series(1, 240) g
JOIN standort_profil p ON p.rang = CASE
                                       WHEN g <= 70 THEN 1
                                       WHEN g <= 110 THEN 2
                                       WHEN g <= 150 THEN 3
                                       ELSE 4 + g % 5
                                   END;

-- 20'000 fiktive Kunden, jeder 97. gesperrt.
INSERT INTO kunde (name, email, gesperrt)
SELECT 'Kunde ' || lpad(g::text, 5, '0'),
       'kunde' || lpad(g::text, 5, '0') || '@example.test',
       g % 97 = 0
FROM generate_series(1, 20000) g;

-- Reservationen pro Fahrzeug als lückenlose Folge: Lücke, Dauer, Lücke, Dauer, ...
-- Mittlere Dauer 13.2 h; die mittlere Lücke ist so gewählt, dass jede Folge rund 27 Monate abdeckt.
CREATE TEMP TABLE slot ON COMMIT DROP AS
WITH basis AS (
    SELECT f.id                          AS fahrzeug_id,
           f.standort_id,
           n,
           f.kennzeichen || '/' || n     AS schluessel,
           p.reservationen_pro_fahrzeug
    FROM fahrzeug f
    JOIN standort_profil p ON p.standort_id = f.standort_id
    CROSS JOIN LATERAL generate_series(1, p.reservationen_pro_fahrzeug) n
), zufall AS (
    SELECT fahrzeug_id,
           standort_id,
           n,
           schluessel,
           pg_temp.zufall(schluessel || '/luecke') * 2 * (19700.0 / reservationen_pro_fahrzeug - 13.2) AS luecke_h,
           CASE
               WHEN pg_temp.zufall(schluessel || '/art') < 0.8 THEN 1 + pg_temp.zufall(schluessel || '/dauer') * 10
               ELSE 12 + pg_temp.zufall(schluessel || '/dauer') * 60
           END                                                    AS dauer_h,
           pg_temp.zufall(schluessel || '/status')                AS z_status,
           pg_temp.zufall(schluessel || '/kunde')                 AS z_kunde,
           5 + floor(pg_temp.zufall(schluessel || '/km') * 120)::int AS km
    FROM basis
)
SELECT *,
       date_trunc('minute', timestamptz '2024-10-01 00:00+02'
           + make_interval(secs => 3600 * (sum(luecke_h + dauer_h) OVER w - dauer_h))) AS beginn,
       date_trunc('minute', timestamptz '2024-10-01 00:00+02'
           + make_interval(secs => 3600 * sum(luecke_h + dauer_h) OVER w))             AS ende
FROM zufall
WINDOW w AS (PARTITION BY fahrzeug_id ORDER BY n);

-- Kunden ungleich verteilt: niedrige Kundennummern buchen viel häufiger.
INSERT INTO reservation (kunde_id, fahrzeug_id, standort_id, beginn, ende, status, erstellt_am)
SELECT 1 + floor(19999 * power(z_kunde, 2.5))::int,
       fahrzeug_id,
       standort_id,
       beginn,
       ende,
       CASE
           WHEN z_status < 0.15 THEN 'STORNIERT'
           WHEN beginn < timestamptz '2026-10-01 00:00+02' THEN 'ABGESCHLOSSEN'
           ELSE 'AKTIV'
       END,
       beginn - interval '3 days'
FROM slot
ORDER BY beginn, fahrzeug_id;

-- Fahrten zu allen abgeschlossenen Reservationen; km_start fortlaufend pro Fahrzeug.
INSERT INTO fahrt (reservation_id, km_start, km_ende, rueckgabe_am)
SELECT x.reservation_id,
       x.km_start,
       x.km_start + x.km,
       x.ende - make_interval(mins => floor(pg_temp.zufall(x.schluessel || '/rueckgabe') * 30)::int)
FROM (SELECT r.id AS reservation_id,
             r.ende,
             s.km,
             s.schluessel,
             coalesce(sum(s.km) OVER (PARTITION BY r.fahrzeug_id ORDER BY r.beginn
                 ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)::int AS km_start
      FROM reservation r
      JOIN slot s ON s.fahrzeug_id = r.fahrzeug_id AND s.beginn = r.beginn
      WHERE r.status = 'ABGESCHLOSSEN') x
ORDER BY x.reservation_id;

-- km_stand der Fahrzeuge = Kilometerstand nach der letzten Fahrt.
UPDATE fahrzeug fz
SET km_stand = coalesce((SELECT max(f.km_ende)
                         FROM fahrt f
                         JOIN reservation r ON r.id = f.reservation_id
                         WHERE r.fahrzeug_id = fz.id), 0);

COMMIT;

-- VACUUM setzt die Visibility Map; erst dann sind Index Only Scans ohne Heap-Zugriffe möglich
-- (im Betrieb erledigt das Autovacuum). ANALYZE aktualisiert die Statistiken für den Planer.
VACUUM ANALYZE;

SELECT 'fahrzeug' AS tabelle, count(*) AS anzahl FROM fahrzeug
UNION ALL SELECT 'kunde', count(*) FROM kunde
UNION ALL SELECT 'reservation', count(*) FROM reservation
UNION ALL SELECT 'fahrt', count(*) FROM fahrt;
