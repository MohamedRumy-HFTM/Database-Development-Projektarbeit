# Prüfanleitung

Alle Fachfunktionen A1–A7 in der Oberfläche, Schritt für Schritt mit dem erwarteten Ergebnis.
Start, Testdaten und Voraussetzungen: siehe [README](../README.md).

Ausgangslage: Testdaten frisch geladen, Anwendung neu gestartet, http://localhost:8080 offen.
Die Schritte bauen aufeinander auf. «Morgen» ist der Tag nach der Prüfung; Buchen füllt morgen 10:00–14:00 vor.
Fehlermeldungen beginnen mit dem HTTP-Status (**400**, **409**), wie ihn die API liefert.

| Nr | Vorgehen | Erwartet | Anf. |
|---|---|---|---|
| | **Fahrzeuge** | | |
| 1 | «Fahrzeug erfassen»: `BE 9901`, `Ford Transit`, Transporter, km-Stand 1000, Pieterlen | «Erfasst: BE 9901, Ford Transit in Pieterlen.» | A1 |
| 2 | Nochmals erfassen mit Kennzeichen `BE 100001` | **409** Kennzeichen existiert bereits | A1 |
| 3 | Kennzeichen `BE-9901` | **400** mit Feldfehler `kennzeichen` | A1 |
| 4 | `BE 9902` erfassen (sonst gleich), danach in der Liste (Standort Pieterlen) «Ausser Betrieb nehmen» | Status «Ausser Betrieb» | A1 |
| 5 | Standort Biel Bahnhof, bei `BE 100001` «Bearbeiten», Standort Lyss Zentrum, «Speichern» | **409** Standortwechsel nicht möglich (offene Reservationen aus den Testdaten) | A1 |
| 6 | Bei `BE 100001` «Ausser Betrieb nehmen» | **409** Ausserbetriebnahme nicht möglich | A1 |
| 7 | Fahrzeuge zusätzlich in einem zweiten Tab öffnen. Tab 1: `BE 9901` bearbeiten, Modell `Ford Transit Custom`, speichern. Tab 2, ohne neu zu laden: `BE 9901` bearbeiten und speichern | Tab 1 gespeichert, Tab 2 **409** «zwischenzeitlich geändert» | A1 |
| | **Kunden** | | |
| 8 | «Kunde erfassen»: `Mia Muster`, `mia.muster@example.ch` | «Erfasst: Mia Muster …» | A2 |
| 9 | Nochmals erfassen: `Mia Muster`, `MIA.Muster@Example.ch` | **409** E-Mail existiert bereits | A2 |
| 10 | Suche `Kunde 00002`, «Sperren» | «Kunde 00002 ist gesperrt …» | A2 |
| | **Buchen** | | |
| 11 | Kunde `Mia` wählen; Standort Pieterlen, Kategorie Transporter; «Freie Fahrzeuge suchen» | `BE 9901` in der Liste, `BE 9902` (ausser Betrieb) nicht | A6, A1 |
| 12 | Bei `BE 9901` «Reservieren» | «Reserviert: BE 9901 für Mia Muster, …, Abholung in Pieterlen.» | A3 |
| 13 | Zeit 12:00–16:00, suchen | `BE 9901` fehlt, weil es sich mit 10–14 überschneidet | A6, A3 |
| 14 | Zeit 14:00–16:00, suchen, `BE 9901` reservieren | Angeboten und reserviert: direkt anschliessend ist erlaubt | A6, A3 |
| 15 | Zeit 16:00–20:00, suchen. Unter «Gleichzeitig buchen»: Fahrzeug `BE 9901`, zweiter Kunde `Kunde 00003`, «Beide Anfragen gleichzeitig senden» | Anfrage A (16–20) und B (18–22) laufen gleichzeitig: eine **201**, die andere **409** mit Grund | A3 |
| 16 | Beginn und Ende auf gestern 10:00–14:00, suchen, ein Fahrzeug reservieren | **400** Beginn in der Vergangenheit | A3 |
| 17 | «Anderen Kunden wählen», `Kunde 00002` (Schritt 10); morgen 20:00–22:00, alle Standorte und Kategorien, ein Fahrzeug reservieren | **409** Kunde gesperrt | A2 |
| 18 | Ende vor Beginn, suchen | **400** «bis muss nach von liegen» | A6 |
| | **Reservationen** | | |
| 19 | Kunde `Mia` wählen | Reservationen von morgen, neueste zuerst | A4 |
| 20 | Bei 10:00–14:00 «Stornieren» | «Storniert: …», die Zeile bleibt mit Status Storniert | A3 |
| 21 | Buchen: `Mia`, Zeit 12:00–14:00, Pieterlen, Transporter, suchen | `BE 9901` ist wieder frei | A3 |
| 22 | Reservationen: `Kunde 00001`; «Weiter», «Pro Seite» 50, Status «Storniert» | 2'314 Reservationen auf 116 Seiten; mit Filter nur die 342 stornierten | A4 |
| | **Nutzung und Fahrtabschluss** | | |
| 23 | Nutzung: Monat August 2026, «Auswerten», danach «Erneut abfragen». Dann Monat September 2026, «Auswerten» | August: «Server» zuerst einige ms (Datenbank), danach meist unter 1 ms (Cache). September: je Abholstandort Fahrten, Stunden, Kilometer, Fahrzeuge und Auslastung, z. B. Biel Bahnhof 70 Fahrzeuge und 36.9 %; Pieterlen 19 Fahrzeuge (mit `BE 9901`, ohne `BE 9902` ausser Betrieb). Je Fahrzeug die Auslastung | A7 |
| 24 | Monat Oktober 2026, «Auswerten» | Alle Standorte mit 0: Nach dem Stichtag gibt es noch keine abgeschlossenen Fahrten | A7 |
| 25 | Reservationen: `Kunde 11459`, bei `BE 100064` «Fahrt abschliessen»; km-Stand `31000`, Rückgabe 7.10.2026 05:00 | **400** km-Stand kleiner als bisher (31'150); die Reservation bleibt aktiv | A5 |
| 26 | km-Stand `31270`, «Fahrt abschliessen» | «120 km gefahren, neuer km-Stand 31'270»; Status Abgeschlossen, kein zweiter Abschluss angeboten. Unter Fahrzeuge (Biel Bahnhof, Seite 4) hat `BE 100064` jetzt 31'270 km | A5 |
| 27 | Nutzung: Oktober 2026, «Auswerten» | Biel Bahnhof 1 Fahrt, 12.7 Stunden, 120 km, ohne Neustart: Der Abschluss hat den Cache geleert | A7 |
| 28 | Fahrzeuge: `BE 9903` in Pieterlen erfassen (sonst wie Schritt 1). Danach Nutzung: September 2026, «Auswerten» | Pieterlen jetzt mit 20 Fahrzeugen und 19.8 % statt 20.9 %, ohne Neustart: Das neue Fahrzeug hat den Cache geleert | A7, A1 |

Den Ausgangszustand stellen ein erneuter Lauf von `scripts/testdaten.sql` und ein Neustart der Anwendung wieder her.

## Nur über Swagger UI oder Tests

Einige Regeln lässt die Oberfläche gar nicht erst zu. Sie gelten trotzdem in der API:

| Prüffall | Grund | In Swagger UI | Test |
|---|---|---|---|
| Storno nach Beginn → 409 | Storno wird nur vor Beginn angeboten | `POST /api/reservationen/108214/stornieren` (Kunde 16729, begann am 2.10.2026) | `ReservationApiTest.stornoNachBeginnGibt409` |
| Zweiter Abschluss → 409 | Kein Knopf nach dem Abschluss | Nach Schritt 26: `POST /api/reservationen/108809/abschliessen` mit `{"kmEnde": 31300, "rueckgabeAm": "2026-10-07T05:00:00+02:00"}` | `FahrtAbschlussTest.zweiterAbschlussGibt409` |
| Reservation für ein Fahrzeug ausser Betrieb → 409 | Die Suche bietet es nicht an | ID von `BE 9902` aus `GET /api/fahrzeuge?standortId=5`, dann `POST /api/reservationen` mit `{"kundeId": 1, "fahrzeugId": <ID>, "beginn": "2027-06-01T10:00:00+02:00", "ende": "2027-06-01T14:00:00+02:00"}` | `ReservationApiTest.fahrzeugAusserBetriebGibt409` |
| Reservation mit Ende ≤ Beginn → 400 | Die Suche prüft den Zeitraum vorher | `POST /api/reservationen` mit `beginn` gleich `ende` | `ReservationApiTest.endeVorOderGleichBeginnGibt400` |
| Seitengrösse über 100 → 400 | Die Oberfläche bietet 10, 20 und 50 | `GET /api/kunden/1/reservationen?groesse=101` | `KundenReservationApiTest.groesseUeber100Gibt400` |
| Zweite Transaktion wartet auf die erste | Nur im Ablauf der Datenbank sichtbar | – | `ReservationNebenlaeufigkeitTest` |
| Rollback nach Schreibzugriff in A5 | Die Oberfläche zeigt nur das Ergebnis | – | `FahrtAbschlussTest` |
| Anzahl SQL-Abfragen (N+1) | Nur im SQL-Log sichtbar | – | `ReservationslisteAbfragenTest` |
| Cache-Treffer ohne SQL | Die Oberfläche zeigt nur die Serverzeit | – | `NutzungCacheTest` |
