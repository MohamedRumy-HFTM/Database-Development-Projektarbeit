#!/usr/bin/env bash
# Zwei gleichzeitige, überschneidende Reservationen für dasselbe Fahrzeug.
# Erwartet: genau eine Antwort 201 und eine 409 – egal, welche zuerst ankommt.
#
# Nutzung: scripts/demo-konflikt.sh <fahrzeugId> <kundeIdA> <kundeIdB> [Tag, z. B. 2027-10-04]
# Für einen zweiten Lauf einen anderen Tag oder ein anderes Fahrzeug wählen.
set -euo pipefail

URL="${URL:-http://localhost:8080/api/reservationen}"
FAHRZEUG="${1:?Fahrzeug-ID angeben}"
KUNDE_A="${2:?Kunde A angeben}"
KUNDE_B="${3:?Kunde B angeben}"
TAG="${4:-2027-10-04}"

reservieren() {
  local kunde="$1" von="$2" bis="$3"
  curl -s -o /dev/null -w "Kunde ${kunde}, ${von}–${bis} Uhr: %{http_code}\n" \
    -H 'Content-Type: application/json' \
    -d "{\"kundeId\": ${kunde}, \"fahrzeugId\": ${FAHRZEUG},
         \"beginn\": \"${TAG}T${von}:00:00+02:00\", \"ende\": \"${TAG}T${bis}:00:00+02:00\"}" \
    "$URL"
}

reservieren "$KUNDE_A" 10 14 &
reservieren "$KUNDE_B" 12 16 &
wait
