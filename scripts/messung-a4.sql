-- T10: Messung der A4-Abfrage (Reservationen eines Kunden) ohne und mit Index ix_reservation_kunde_beginn.
--
-- Die SQL-Texte entsprechen genau dem, was Hibernate für A4 erzeugt (siehe ReservationslisteAbfragenTest).
-- Je Szenario 1 Aufwärmlauf und 5 gemessene Läufe; ausgewertet wird der Median der Execution Time
-- aus EXPLAIN ANALYZE (ohne Planung und Netzwerk).
-- «Ohne Index» läuft in einer Transaktion, die den Index entfernt und am Ende zurückgerollt wird.
-- So lässt sich der Vergleich jederzeit wiederholen. Die Anwendung währenddessen nicht benutzen:
-- DROP INDEX sperrt die Tabelle bis zum ROLLBACK.
--
-- Voraussetzung: Testdaten geladen (scripts/testdaten.sql).
-- Aufruf:
--   docker compose exec -T db psql -U seeland -d seeland < scripts/messung-a4.sql

\set ON_ERROR_STOP on
\pset footer off
-- Definierter Ausgangszustand wie nach Autovacuum: Visibility Map gesetzt, Statistiken aktuell.
VACUUM ANALYZE reservation;

\echo
\echo '== Umgebung und Datenmenge =='
SELECT version() AS postgresql;
SELECT current_setting('shared_buffers') AS shared_buffers, current_setting('work_mem') AS work_mem,
       (SELECT count(*) FROM reservation) AS reservationen,
       pg_size_pretty(pg_relation_size('reservation')) AS tabelle;

-- Messobjekte: der Kunde mit den meisten Reservationen und ein typischer Kunde (Median)
SELECT kunde_id AS vielfahrer, count(*) AS vielfahrer_anzahl
FROM reservation GROUP BY kunde_id ORDER BY count(*) DESC, kunde_id LIMIT 1 \gset
WITH je_kunde AS (SELECT kunde_id, count(*) AS anzahl FROM reservation GROUP BY kunde_id),
     median AS (SELECT percentile_disc(0.5) WITHIN GROUP (ORDER BY anzahl) AS anzahl FROM je_kunde)
SELECT min(j.kunde_id) AS typisch, min(m.anzahl) AS typisch_anzahl
FROM je_kunde j JOIN median m ON j.anzahl = m.anzahl \gset
\echo 'Vielfahrer: Kunde' :vielfahrer 'mit' :vielfahrer_anzahl 'Reservationen'
\echo 'Typisch:    Kunde' :typisch 'mit' :typisch_anzahl 'Reservationen'

CREATE TEMP TABLE szenario (nr int PRIMARY KEY, name text, sql text);
INSERT INTO szenario VALUES
    (1, 'Vielfahrer, Seite 1', format($q$
        select r1_0.id,r1_0.beginn,r1_0.ende,r1_0.erstellt_am,r1_0.fahrzeug_id,f1_0.id,f1_0.in_betrieb,
               f1_0.kategorie,f1_0.kennzeichen,f1_0.km_stand,f1_0.modell,f1_0.standort_id,f1_0.version,
               r1_0.kunde_id,r1_0.standort_id,s1_0.id,s1_0.name,s1_0.ort,r1_0.status
        from reservation r1_0 join fahrzeug f1_0 on f1_0.id=r1_0.fahrzeug_id
             join standort s1_0 on s1_0.id=r1_0.standort_id
        where r1_0.kunde_id=%s order by r1_0.beginn desc,r1_0.id desc fetch first 20 rows only$q$, :vielfahrer)),
    (2, 'Vielfahrer, Seite 3', format($q$
        select r1_0.id,r1_0.beginn,r1_0.ende,r1_0.erstellt_am,r1_0.fahrzeug_id,f1_0.id,f1_0.in_betrieb,
               f1_0.kategorie,f1_0.kennzeichen,f1_0.km_stand,f1_0.modell,f1_0.standort_id,f1_0.version,
               r1_0.kunde_id,r1_0.standort_id,s1_0.id,s1_0.name,s1_0.ort,r1_0.status
        from reservation r1_0 join fahrzeug f1_0 on f1_0.id=r1_0.fahrzeug_id
             join standort s1_0 on s1_0.id=r1_0.standort_id
        where r1_0.kunde_id=%s order by r1_0.beginn desc,r1_0.id desc offset 40 rows fetch first 20 rows only$q$,
        :vielfahrer)),
    (3, 'Vielfahrer, Count', format('select count(r1_0.id) from reservation r1_0 where r1_0.kunde_id=%s',
        :vielfahrer)),
    (4, 'Typischer Kunde, Seite 1', format($q$
        select r1_0.id,r1_0.beginn,r1_0.ende,r1_0.erstellt_am,r1_0.fahrzeug_id,f1_0.id,f1_0.in_betrieb,
               f1_0.kategorie,f1_0.kennzeichen,f1_0.km_stand,f1_0.modell,f1_0.standort_id,f1_0.version,
               r1_0.kunde_id,r1_0.standort_id,s1_0.id,s1_0.name,s1_0.ort,r1_0.status
        from reservation r1_0 join fahrzeug f1_0 on f1_0.id=r1_0.fahrzeug_id
             join standort s1_0 on s1_0.id=r1_0.standort_id
        where r1_0.kunde_id=%s order by r1_0.beginn desc,r1_0.id desc fetch first 20 rows only$q$, :typisch));

-- Median der Execution Time aus 5 Läufen (nach 1 Aufwärmlauf)
CREATE FUNCTION pg_temp.median_ms(abfrage text) RETURNS numeric LANGUAGE plpgsql AS $$
DECLARE
    plan   json;
    zeiten numeric[] := '{}';
BEGIN
    EXECUTE 'EXPLAIN (ANALYZE, FORMAT JSON) ' || abfrage INTO plan;
    FOR lauf IN 1..5 LOOP
        EXECUTE 'EXPLAIN (ANALYZE, FORMAT JSON) ' || abfrage INTO plan;
        zeiten := zeiten || (plan -> 0 ->> 'Execution Time')::numeric;
    END LOOP;
    RETURN round((SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY z) FROM unnest(zeiten) z)::numeric, 3);
END $$;

-- Kosten beim Schreiben: 10'000 Reservationen einfügen (2050, ohne Überschneidungen), danach zurückrollen.
-- Median aus 5 Läufen nach 1 Aufwärmlauf.
CREATE TEMP TABLE fz AS SELECT row_number() OVER (ORDER BY id) - 1 AS nr, id, standort_id FROM fahrzeug;
CREATE TEMP TABLE kd AS SELECT row_number() OVER (ORDER BY id) - 1 AS nr, id FROM kunde;
CREATE FUNCTION pg_temp.einfuegen_ms(anzahl int) RETURNS numeric LANGUAGE plpgsql AS $$
DECLARE
    zeiten       numeric[] := '{}';
    start        timestamptz;
    fahrzeuge    int := (SELECT count(*) FROM fz);
    kunden       int := (SELECT count(*) FROM kd);
BEGIN
    FOR lauf IN 0..5 LOOP
        BEGIN
            start := clock_timestamp();
            INSERT INTO reservation (kunde_id, fahrzeug_id, standort_id, beginn, ende)
            SELECT k.id, f.id, f.standort_id,
                   timestamptz '2050-01-01 10:00+01' + (g / fahrzeuge) * interval '1 day',
                   timestamptz '2050-01-01 12:00+01' + (g / fahrzeuge) * interval '1 day'
            FROM generate_series(0, anzahl - 1) g
            JOIN fz f ON f.nr = g % fahrzeuge
            JOIN kd k ON k.nr = (g * 7919) % kunden;
            IF lauf > 0 THEN
                zeiten := zeiten || (extract(epoch FROM clock_timestamp() - start) * 1000)::numeric;
            END IF;
            RAISE EXCEPTION 'zuruecksetzen';
        EXCEPTION WHEN raise_exception THEN
            NULL;
        END;
    END LOOP;
    RETURN round((SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY z) FROM unnest(zeiten) z)::numeric, 1);
END $$;

\echo
\echo '== Ohne Index =='
BEGIN;
DROP INDEX IF EXISTS ix_reservation_kunde_beginn;
SELECT pg_temp.median_ms((SELECT sql FROM szenario WHERE nr = 1)) AS ohne1,
       pg_temp.median_ms((SELECT sql FROM szenario WHERE nr = 2)) AS ohne2,
       pg_temp.median_ms((SELECT sql FROM szenario WHERE nr = 3)) AS ohne3,
       pg_temp.median_ms((SELECT sql FROM szenario WHERE nr = 4)) AS ohne4 \gset
\echo 'Plan Vielfahrer, Seite 1:'
SELECT 'EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) ' || sql FROM szenario WHERE nr = 1 \gexec
ROLLBACK;

\echo
\echo '== Mit Index (falls vorhanden) =='
SELECT coalesce(pg_size_pretty(pg_relation_size(to_regclass('ix_reservation_kunde_beginn'))), 'kein Index')
       AS indexgroesse \gset
\echo 'Grösse ix_reservation_kunde_beginn:' :indexgroesse
SELECT pg_temp.median_ms((SELECT sql FROM szenario WHERE nr = 1)) AS mit1,
       pg_temp.median_ms((SELECT sql FROM szenario WHERE nr = 2)) AS mit2,
       pg_temp.median_ms((SELECT sql FROM szenario WHERE nr = 3)) AS mit3,
       pg_temp.median_ms((SELECT sql FROM szenario WHERE nr = 4)) AS mit4 \gset
\echo 'Plan Vielfahrer, Seite 1:'
SELECT 'EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) ' || sql FROM szenario WHERE nr = 1 \gexec
\echo 'Plan Vielfahrer, Count:'
SELECT 'EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) ' || sql FROM szenario WHERE nr = 3 \gexec

-- Schreibkosten zuletzt: Auch zurückgerollte INSERTs verändern Tabellenseiten (Visibility Map)
-- und würden sonst die Lesemessungen danach verfälschen.
\echo
\echo '== Schreibkosten =='
BEGIN;
DROP INDEX IF EXISTS ix_reservation_kunde_beginn;
SELECT pg_temp.einfuegen_ms(10000) AS ohne_insert \gset
ROLLBACK;
SELECT pg_temp.einfuegen_ms(10000) AS mit_insert \gset

\echo
\echo '== Ergebnis (Median aus 5 Läufen, ms) =='
SELECT szenario, ohne_index, mit_index, round(ohne_index / nullif(mit_index, 0), 1) AS faktor
FROM (VALUES
          (1, 'Vielfahrer, Seite 1', :ohne1::numeric, :mit1::numeric),
          (2, 'Vielfahrer, Seite 3', :ohne2::numeric, :mit2::numeric),
          (3, 'Vielfahrer, Count', :ohne3::numeric, :mit3::numeric),
          (4, 'Typischer Kunde, Seite 1', :ohne4::numeric, :mit4::numeric),
          (5, 'INSERT 10''000 Reservationen', :ohne_insert::numeric, :mit_insert::numeric))
     AS t(nr, szenario, ohne_index, mit_index)
ORDER BY nr;
