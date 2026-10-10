// A4 Reservationen eines Kunden, A3 stornieren, A5 Fahrt abschliessen.

import { lesen, senden } from '../api.js';
import {
  alsIso, fehlerAnzeigen, fuerEingabe, ganzzahl, h, knopfMitBestaetigung, meldung, meldungsbereich, optionen,
  schild, seitenwahl, zeitraum,
} from '../dom.js';
import { kundenwahl } from '../kundenwahl.js';

const STATUS = [['AKTIV', 'Aktiv'], ['ABGESCHLOSSEN', 'Abgeschlossen'], ['STORNIERT', 'Storniert']];
const statusText = (wert) => STATUS.find(([s]) => s === wert)?.[1] ?? wert;

export async function anzeigen(ziel, parameter) {
  const info = meldungsbereich();
  const liste = h('div', { class: 'rahmen' });
  const status = h('select', { onchange: () => laden(0) }, h('option', { value: '' }, 'Alle'), optionen(STATUS));
  const groesse = h('select', { onchange: () => laden(0) }, optionen([[10, '10'], [20, '20'], [50, '50']], 20));
  let kunde = null;
  let seite = 0;

  let vorauswahl = null;
  if (parameter.get('kunde')) {
    try {
      vorauswahl = await lesen(`/api/kunden/${encodeURIComponent(parameter.get('kunde'))}`);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  const wahl = kundenwahl({
    beschriftung: 'Kunde',
    gewaehlt: (gewaehlt) => {
      kunde = gewaehlt;
      history.replaceState(null, '', gewaehlt ? `#/reservationen?kunde=${gewaehlt.id}` : '#/reservationen');
      info.hidden = true;
      laden(0);
    },
  });

  async function laden(neueSeite) {
    if (!kunde) {
      liste.replaceChildren(h('p', { class: 'leer' }, 'Wähle einen Kunden, um seine Reservationen zu sehen.'));
      return;
    }
    seite = neueSeite;
    try {
      const daten = await lesen(`/api/kunden/${kunde.id}/reservationen`,
        { status: status.value, seite, groesse: groesse.value });
      zeichnen(daten);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  function zeichnen(daten) {
    if (daten.inhalt.length === 0) {
      liste.replaceChildren(h('p', { class: 'leer' }, status.value
        ? 'Keine Reservationen mit diesem Status.'
        : 'Dieser Kunde hat noch keine Reservationen.'));
      return;
    }
    const jetzt = new Date();
    const zeilen = h('tbody');
    for (const reservation of daten.inhalt) {
      const zeile = h('tr', {},
        h('td', {}, zeitraum(reservation.beginn, reservation.ende)),
        h('td', {}, schild(reservation.kennzeichen)),
        h('td', {}, reservation.standortName),
        h('td', {}, h('span', { class: `status status-${reservation.status}` }, statusText(reservation.status))),
        h('td', { class: 'aktionen' }, aktionen(reservation, jetzt)));
      zeilen.append(zeile);
    }
    liste.replaceChildren(
      h('table', {},
        h('thead', {}, h('tr', {}, h('th', {}, 'Zeitraum'), h('th', {}, 'Kennzeichen'), h('th', {}, 'Abholstandort'),
          h('th', {}, 'Status'), h('th'))),
        zeilen),
      seitenwahl(daten, laden));
  }

  function aktionen(reservation, jetzt) {
    if (reservation.status !== 'AKTIV') return null;
    if (new Date(reservation.beginn) > jetzt) {
      return knopfMitBestaetigung('Stornieren', 'Wirklich stornieren?', () => stornieren(reservation));
    }
    return h('button', {
      class: 'knopf knopf-zeile knopf-klein', type: 'button',
      onclick: (e) => abschlussZeigen(e.currentTarget.closest('tr'), reservation),
    }, 'Fahrt abschliessen');
  }

  async function stornieren(reservation) {
    try {
      await senden(`/api/reservationen/${reservation.id}/stornieren`);
      meldung(info, 'ok', `Storniert: ${reservation.kennzeichen}, ${zeitraum(reservation.beginn, reservation.ende)}.`);
      await laden(seite);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  async function abschlussZeigen(zeile, reservation) {
    liste.querySelector('tr.bearbeiten')?.remove();
    const fahrzeug = await lesen(`/api/fahrzeuge/${reservation.fahrzeugId}`);
    const km = h('input', { type: 'number', min: 0, required: true, value: fahrzeug.kmStand });
    const rueckgabe = h('input', { type: 'datetime-local', required: true, value: fuerEingabe(new Date()) });
    const formular = h('form', {
      class: 'zeile',
      onsubmit: async (e) => {
        e.preventDefault();
        try {
          const fahrt = await senden(`/api/reservationen/${reservation.id}/abschliessen`,
            { kmEnde: Number(km.value), rueckgabeAm: alsIso(rueckgabe.value) });
          meldung(info, 'ok', `Fahrt abgeschlossen: ${reservation.kennzeichen}, ${ganzzahl(fahrt.gefahreneKm)} km gefahren, `
            + `neuer km-Stand ${ganzzahl(fahrt.kmEnde)}.`);
          await laden(seite);
        } catch (fehler) {
          fehlerAnzeigen(info, fehler);
        }
      },
    },
    h('label', {}, `km-Stand bei Rückgabe (bisher ${ganzzahl(fahrzeug.kmStand)})`, km),
    h('label', {}, 'Rückgabe', rueckgabe),
    h('button', { class: 'knopf', type: 'submit' }, 'Fahrt abschliessen'),
    h('button', { class: 'knopf knopf-leise', type: 'button', onclick: () => formularZeile.remove() }, 'Abbrechen'));
    const formularZeile = h('tr', { class: 'bearbeiten' }, h('td', { colspan: 5 }, formular));
    zeile.after(formularZeile);
    km.focus();
  }

  ziel.append(
    h('h1', {}, 'Reservationen'),
    h('p', { class: 'einleitung' }, 'Reservationen eines Kunden, neueste zuerst. Laufende Fahrten werden hier abgeschlossen.'),
    wahl.element,
    h('div', { class: 'zeile', style: 'margin-top:1.2rem' },
      h('label', {}, 'Status', status),
      h('label', {}, 'Pro Seite', groesse)),
    info,
    liste);

  if (vorauswahl) {
    wahl.waehlen(vorauswahl);
  } else {
    laden(0);
  }
}
