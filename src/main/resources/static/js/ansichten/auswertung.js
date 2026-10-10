// A7 Nutzung je Abholstandort und je Fahrzeug für einen Monat.

import { alleStandorte, lesen, lesenMitServerzeit } from '../api.js';
import { fehlerAnzeigen, ganzzahl, h, meldungsbereich, optionen, schild, zahl } from '../dom.js';

const ANZAHL_KURZ = 15;

function balken(anteil, text) {
  return h('div', { class: 'balken' },
    h('div', { class: 'balken-spur' }, h('div', { class: 'balken-wert', style: `width:${Math.min(100, anteil * 100)}%` })),
    h('span', { class: 'balken-zahl' }, text));
}

export async function anzeigen(ziel) {
  const standorte = await alleStandorte();
  const info = meldungsbereich();
  const heute = new Date();
  const vormonat = new Date(heute.getFullYear(), heute.getMonth() - 1, 1);
  const monat = h('input', {
    type: 'month', required: true,
    value: `${vormonat.getFullYear()}-${String(vormonat.getMonth() + 1).padStart(2, '0')}`,
  });
  const antwortzeit = h('span', { class: 'klein' });
  const standortListe = h('div', { class: 'rahmen' });
  const fahrzeugFilter = h('select', { onchange: () => ladenFahrzeuge() },
    h('option', { value: '' }, 'Alle Standorte'), optionen(standorte.map((s) => [s.id, s.name])));
  const fahrzeugListe = h('div', { class: 'rahmen' });
  let alleZeigen = false;

  /** Monatsgrenzen in Ortszeit, als Zeitraum [von, bis). */
  function zeitraum() {
    const [jahr, monatNr] = monat.value.split('-').map(Number);
    return { von: new Date(jahr, monatNr - 1, 1).toISOString(), bis: new Date(jahr, monatNr, 1).toISOString() };
  }

  async function ladenStandorte() {
    try {
      const { daten, serverMs } = await lesenMitServerzeit('/api/auswertungen/standorte', zeitraum());
      antwortzeit.textContent = serverMs === null ? '' : `Server: ${zahl(serverMs, 2)} ms`;
      const meiste = Math.max(1, ...daten.map((d) => d.anzahlFahrten));
      standortListe.replaceChildren(h('table', {},
        h('thead', {}, h('tr', {}, h('th', {}, 'Abholstandort'), h('th', {}, 'Fahrten'),
          h('th', { class: 'zahl' }, 'Nutzungsstunden'), h('th', { class: 'zahl' }, 'Kilometer'),
          h('th', { class: 'zahl' }, 'Fahrzeuge'), h('th', {}, 'Auslastung'))),
        h('tbody', {}, daten.map((d) => h('tr', {},
          h('td', {}, d.standortName),
          h('td', {}, balken(d.anzahlFahrten / meiste, ganzzahl(d.anzahlFahrten))),
          h('td', { class: 'zahl' }, zahl(d.nutzungsstunden, 1)),
          h('td', { class: 'zahl' }, ganzzahl(d.km)),
          h('td', { class: 'zahl' }, ganzzahl(d.anzahlFahrzeuge)),
          h('td', {}, d.auslastungProzent === null
            ? h('span', { class: 'klein' }, 'kein Fahrzeug')
            : balken(d.auslastungProzent / 100, `${zahl(d.auslastungProzent, 1)} %`)))))));
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  async function ladenFahrzeuge() {
    try {
      const daten = await lesen('/api/auswertungen/fahrzeuge', { ...zeitraum(), standortId: fahrzeugFilter.value });
      if (daten.length === 0) {
        fahrzeugListe.replaceChildren(h('p', { class: 'leer' }, 'Keine Fahrzeuge erfasst.'));
        return;
      }
      const sichtbar = alleZeigen ? daten : daten.slice(0, ANZAHL_KURZ);
      fahrzeugListe.replaceChildren(
        h('table', {},
          h('thead', {}, h('tr', {}, h('th', {}, 'Kennzeichen'), h('th', {}, 'Standort heute'),
            h('th', { class: 'zahl' }, 'Fahrten'), h('th', { class: 'zahl' }, 'Stunden'), h('th', { class: 'zahl' }, 'km'),
            h('th', {}, 'Auslastung'))),
          h('tbody', {}, sichtbar.map((d) => h('tr', {},
            h('td', {}, schild(d.kennzeichen)),
            h('td', {}, d.standortName),
            h('td', { class: 'zahl' }, ganzzahl(d.anzahlFahrten)),
            h('td', { class: 'zahl' }, zahl(d.nutzungsstunden, 1)),
            h('td', { class: 'zahl' }, ganzzahl(d.km)),
            h('td', {}, balken(d.auslastungProzent / 100, `${zahl(d.auslastungProzent, 1)} %`)))))),
        daten.length > ANZAHL_KURZ
          ? h('p', {}, h('button', {
            class: 'knopf knopf-leise knopf-klein', type: 'button',
            onclick: () => { alleZeigen = !alleZeigen; ladenFahrzeuge(); },
          }, alleZeigen ? `Nur die ${ANZAHL_KURZ} meistgenutzten zeigen` : `Alle ${daten.length} Fahrzeuge zeigen`))
          : null);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  const formular = h('form', {
    class: 'zeile',
    onsubmit: (e) => { e.preventDefault(); info.hidden = true; ladenStandorte(); ladenFahrzeuge(); },
  },
  h('label', {}, 'Monat', monat),
  h('button', { class: 'knopf', type: 'submit' }, 'Auswerten'));

  ziel.append(
    h('h1', {}, 'Nutzung'),
    h('p', { class: 'einleitung' }, 'Abgeschlossene Fahrten eines Monats, zugeordnet nach Reservationsbeginn und Abholstandort. '
      + 'Stornierte und offene Reservationen zählen nicht.'),
    formular,
    info,
    h('div', { class: 'abschnitt-kopf' },
      h('h2', {}, 'Je Abholstandort'),
      h('p', {}, antwortzeit, ' ',
        h('button', { class: 'knopf knopf-leise knopf-klein', type: 'button', onclick: ladenStandorte }, 'Erneut abfragen'))),
    h('p', { class: 'klein' }, 'Auslastung = Nutzungsstunden geteilt durch (Fahrzeuge in Betrieb am Standort × Stunden des Monats). '
      + 'Die Auswertung wird pro Monat zwischengespeichert: Die erste Abfrage liest aus der Datenbank, jede weitere kommt aus dem '
      + 'Cache (Server meist unter 1 ms), bis eine Fahrt abgeschlossen oder der Fahrzeugbestand eines Standorts geändert wird.'),
    standortListe,
    h('div', { class: 'abschnitt-kopf' },
      h('h2', {}, 'Je Fahrzeug'),
      h('label', {}, 'Standort heute', fahrzeugFilter)),
    h('p', { class: 'klein' }, 'Auslastung = Nutzungsstunden geteilt durch die Stunden des Monats. Meistgenutzte zuerst.'),
    fahrzeugListe);

  await Promise.all([ladenStandorte(), ladenFahrzeuge()]);
}
