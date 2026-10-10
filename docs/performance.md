# Performance

Messungen zu T9 (Datenmenge), T10 (Index), T11 (ORM-Ladeverhalten) sowie Beobachtungen zu A6 und A7.

## Umgebung

| | |
|---|---|
| Datenbank | PostgreSQL 16.15, Linux x86_64, Standardkonfiguration (`shared_buffers` 128 MB, `work_mem` 4 MB) |
| Daten | `scripts/testdaten.sql`, danach `VACUUM ANALYZE` |
| Messwert | `Execution Time` aus `EXPLAIN ANALYZE`, ohne Planung und Netzwerk |
| Verfahren | je Szenario 1 Aufwärmlauf, dann 5 Läufe, Median |
| Cache | alle Seiten im Shared Buffer (`shared hit`), also ohne Plattenzugriffe |

## Datenmenge (T9)

| Tabelle | Zeilen |
|---|---|
| standort | 8 |
| fahrzeug | 240 |
| kunde | 20'000 |
| reservation | 121'880 (12 MB) |
| fahrt | ≈ 91'800 |

Reservationen je Kunde: Maximum 2'314, Median 4, Minimum 1. Gemessen werden der Kunde mit den meisten Reservationen («Vielfahrer») und ein Kunde mit 4 Reservationen («typisch»).

## Index für A4 (T10)

A4 liefert die Reservationen eines Kunden, neueste zuerst, seitenweise. Hibernate erzeugt dafür:

```sql
select … from reservation r1_0
join fahrzeug f1_0 on f1_0.id = r1_0.fahrzeug_id
join standort s1_0 on s1_0.id = r1_0.standort_id
where r1_0.kunde_id = ?
order by r1_0.beginn desc, r1_0.id desc
fetch first ? rows only
```

Bis V8 gibt es auf `reservation` nur den Primärschlüssel und den GiST-Index des Exclusion-Constraints, keinen Index auf `kunde_id`. V9 legt ihn an:

```sql
CREATE INDEX ix_reservation_kunde_beginn ON reservation (kunde_id, beginn DESC, id DESC);
```

Die Spaltenfolge folgt der Abfrage: zuerst die Gleichheitsbedingung, dann die Sortierung in derselben Richtung. `id` ist nötig, weil die Sortierung erst mit `id` eindeutig ist (stabile Seiten, T7). Der `KundenIndexTest` prüft, dass der Plan keinen Sort-Knoten mehr enthält. Mit `beginn ASC` oder nur `(kunde_id)` schlägt er fehl.

### Ergebnis

Messung mit `scripts/messung-a4.sql`, Schema V9. «Ohne Index» entfernt den Index in einer Transaktion, die zurückgerollt wird.

| Szenario | ohne Index | mit Index | Faktor |
|---|---|---|---|
| Vielfahrer, Seite 1 | 8.8 – 13.4 ms | 0.07 ms | > 100 |
| Vielfahrer, Seite 3 | 9.3 – 10.0 ms | 0.15 ms | ≈ 65 |
| Vielfahrer, Count | 6.8 – 8.0 ms | 0.27 ms | 25 – 29 |
| Typischer Kunde, Seite 1 | 6.6 – 9.6 ms | 0.12 ms | ≈ 55 – 80 |

Bereiche = Mediane aus zwei vollständigen Läufen des Skripts. Vor V9 lagen dieselben Abfragen auf V8 zwischen 6.6 und 15.2 ms (Messung 1, Index existierte noch nicht); allein «Vielfahrer, Seite 1» schwankte zwischen 10.1 und 15.1 ms. Das ist das Messrauschen ohne Index; der Effekt des Index liegt um zwei Grössenordnungen darüber.

### Pläne (Vielfahrer, Seite 1)

Ohne Index: ganze Tabelle lesen, 119'566 Zeilen verwerfen, Rest sortieren.

```
Limit (actual time=8.953..8.958 rows=20 loops=1)
  Buffers: shared hit=1500
  ->  Sort
        Sort Key: r1_0.beginn DESC, r1_0.id DESC
        Sort Method: top-N heapsort  Memory: 35kB
        ->  Hash Join … Hash Join
              ->  Seq Scan on reservation r1_0 (actual time=0.005..6.591 rows=2314 loops=1)
                    Filter: (kunde_id = 1)
                    Rows Removed by Filter: 119566
Execution Time: 8.997 ms
```

Mit Index: die ersten 20 Einträge in der richtigen Reihenfolge lesen und aufhören. Kein Sort, 76 statt 1'500 Seiten.

```
Limit (actual time=0.024..0.078 rows=20 loops=1)
  Buffers: shared hit=76
  ->  Nested Loop … Nested Loop
        ->  Index Scan using ix_reservation_kunde_beginn on reservation r1_0 (rows=20 loops=1)
              Index Cond: (kunde_id = 1)
              Buffers: shared hit=11
        ->  Memoize → Index Scan using fahrzeug_pkey …
  ->  Memoize → Index Scan using standort_pkey …
Execution Time: 0.111 ms
```

Count mit Index: reiner Index Only Scan, die Tabelle wird nicht gelesen.

```
Aggregate (actual time=0.367..0.368 rows=1 loops=1)
  ->  Index Only Scan using ix_reservation_kunde_beginn (rows=2314 loops=1)
        Heap Fetches: 0
Execution Time: 0.379 ms
```

Direkt nach dem Laden der Testdaten, ohne `VACUUM`, war die Visibility Map noch nicht gesetzt: Der Count lief als Bitmap Heap Scan über 1'171 Tabellenseiten (4.7 ms). Deshalb endet `testdaten.sql` mit `VACUUM ANALYZE`, und das Messskript beginnt damit. Im Betrieb übernimmt das Autovacuum.

### Kosten

| | |
|---|---|
| Speicher | 4'848 kB, frisch aufgebaut (Tabelle: 12 MB) |
| Schreiben | 10'000 INSERTs: 523 – 528 ms ohne, 529 – 548 ms mit Index, also +1 bis +4 %, im Bereich der Streuung. Den grössten Teil kostet der GiST-Index des Exclusion-Constraints, der bei jedem INSERT geprüft wird. |
| Pflege | Jeder INSERT und jede Änderung von `kunde_id` oder `beginn` aktualisiert den Index. |

Der Index lohnt sich: A4 ist eine häufige Leseabfrage, das Verhältnis Lesen zu Schreiben ist hoch, und der Mehraufwand beim Schreiben ist kaum messbar. Nebeneffekt: Der Fremdschlüssel `fk_reservation_kunde` ist damit indexiert.

## ORM-Ladeverhalten (T11)

A4 als einfache Entity-Liste lädt Fahrzeug und Abholstandort pro Zeile nach (N+1). Mit Fetch Join bleibt es bei einer Abfrage plus Count. Gezählt mit der Hibernate-Statistik (`ReservationslisteAbfragenTest`, 60 Reservationen auf 60 Fahrzeugen an 8 Standorten):

| Seitengrösse | Entity-Liste | Fetch Join |
|---|---|---|
| 5 | 12 Abfragen | 2 |
| 20 | 30 Abfragen | 2 |
| 50 | 60 Abfragen | 2 |

Entity-Liste: 1 Abfrage + 1 Count + 1 je Fahrzeug + 1 je Standort.

## A6 Verfügbarkeitssuche

Die JPQL-Abfrage nutzt den GiST-Index des Exclusion-Constraints, aber nur für `fahrzeug_id`; die Zeitbedingung wird danach gefiltert (rund 16 ms mit den Testdaten). Mit `tstzrange(beginn, ende) && tstzrange(:von, :bis)` könnte PostgreSQL auch den Zeitraum über den Index prüfen (rund 2.6 ms). JPQL kennt den Operator `&&` nicht; dafür wäre eine native Abfrage nötig. Entschieden für JPQL mit DTO-Projektion (T6), weil 16 ms für eine Suche ausreichen.

`STORNIERT` steht als Literal in der Abfrage. Der GiST-Index gilt nur für `status <> 'STORNIERT'`, und PostgreSQL kann ihn nur verwenden, wenn die Bedingung in der Abfrage das sicher erfüllt.

## A7 Cache

Standortauswertung September 2026 über die REST-API: erster Aufruf 85 ms, zweiter Aufruf 5.5 ms aus dem Cache (ohne Datenbankzugriff). Nach jedem Fahrtabschluss wird der Cache geleert, erst nach dem COMMIT (`NutzungCacheTest`).

## Reproduzieren

```bash
docker compose up -d
# Anwendung einmal starten (IntelliJ oder mvn spring-boot:run): Flyway baut das Schema auf, danach beenden
docker compose exec -T db psql -U seeland -d seeland -v ON_ERROR_STOP=1 < scripts/testdaten.sql
docker compose exec -T db psql -U seeland -d seeland < scripts/messung-a4.sql
```

Die Anwendung während der Messung nicht benutzen: «Ohne Index» sperrt die Tabelle bis zum ROLLBACK.
