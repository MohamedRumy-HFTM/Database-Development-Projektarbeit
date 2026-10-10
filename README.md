# Seeland Mobil

Carsharing-Verwaltung mit Spring Boot und PostgreSQL – Projektarbeit Database Development (HFTM).

## Voraussetzungen

- Java 21
- Docker (lokale Datenbank und Tests)

## Datenbank starten

```bash
docker compose up -d
```

Beim Start der Anwendung baut Flyway das Schema auf (`src/main/resources/db/migration`).

## Anwendung starten

In IntelliJ `SeelandMobilApplication` starten oder:

```bash
mvn spring-boot:run
```

## Tests

```bash
mvn test
```

Die Tests laufen mit Testcontainers gegen PostgreSQL 16; Docker muss laufen.

## Testdaten

Nachdem das Schema aufgebaut ist (Anwendung einmal gestartet):

```bash
docker compose exec -T db psql -U seeland -d seeland -v ON_ERROR_STOP=1 < scripts/testdaten.sql
```

Erzeugt 240 Fahrzeuge, 20'000 fiktive Kunden, 121'880 Reservationen und rund 92'000 Fahrten.
Jeder Lauf ersetzt die bestehenden Daten und erzeugt exakt dieselben Datensätze.

## Datenmodell

![ER-Diagramm](docs/er-diagramm.png)

## Migrationen

| Version | Inhalt |
|---|---|
| V1–V5 | Grundschema: `standort`, `fahrzeug`, `kunde`, `reservation` (Exclusion-Constraint gegen Überschneidungen), `fahrt` |
| V6 | Vorgegebene Standorte |
| V7 | Abholstandort in `reservation`; bestehende Reservationen übernehmen den Standort ihres Fahrzeugs |
| V8 | View `v_fahrt_nutzung` mit allen abgeschlossenen Fahrten |
