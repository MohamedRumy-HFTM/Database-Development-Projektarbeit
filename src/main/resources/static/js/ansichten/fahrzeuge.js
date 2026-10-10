// A1 Fahrzeuge erfassen, ändern und ausser Betrieb nehmen.

import { alleStandorte, ersetzen, lesen, senden } from '../api.js';
import {
  KATEGORIEN, fehlerAnzeigen, ganzzahl, h, kategorieText, knopfMitBestaetigung, meldung, meldungsbereich,
  optionen, schild, seitenwahl,
} from '../dom.js';

export async function anzeigen(ziel) {
  const standorte = await alleStandorte();
  const standortOptionen = () => optionen(standorte.map((s) => [s.id, s.name]));
  const info = meldungsbereich();
  const liste = h('div', { class: 'rahmen' });
  const filter = h('select', { onchange: () => { info.hidden = true; laden(0); } },
    h('option', { value: '' }, 'Alle Standorte'), standortOptionen());
  let seite = 0;

  // Erfassen
  const kennzeichen = h('input', { required: true, placeholder: 'BE 12345', size: 10 });
  const modell = h('input', { required: true, maxlength: 60 });
  const kategorie = h('select', {}, optionen(KATEGORIEN));
  const kmStand = h('input', { type: 'number', min: 0, value: 0, required: true });
  const standort = h('select', {}, standortOptionen());
  const erfassen = h('form', {
    class: 'zeile', hidden: true,
    onsubmit: async (e) => {
      e.preventDefault();
      try {
        const neu = await senden('/api/fahrzeuge', {
          kennzeichen: kennzeichen.value.trim(), modell: modell.value.trim(), kategorie: kategorie.value,
          kmStand: Number(kmStand.value), standortId: Number(standort.value),
        });
        meldung(info, 'ok', `Erfasst: ${neu.kennzeichen}, ${neu.modell} in ${neu.standortName}.`);
        erfassen.reset();
        erfassen.hidden = true;
        await laden(seite);
      } catch (fehler) {
        fehlerAnzeigen(info, fehler);
      }
    },
  },
  h('label', {}, 'Kennzeichen', kennzeichen),
  h('label', {}, 'Modell', modell),
  h('label', {}, 'Kategorie', kategorie),
  h('label', {}, 'km-Stand', kmStand),
  h('label', {}, 'Standort', standort),
  h('button', { class: 'knopf', type: 'submit' }, 'Fahrzeug erfassen'),
  h('button', { class: 'knopf knopf-leise', type: 'button', onclick: () => { erfassen.hidden = true; } }, 'Abbrechen'));

  async function laden(neueSeite) {
    seite = neueSeite;
    try {
      const daten = await lesen('/api/fahrzeuge', { standortId: filter.value, seite, groesse: 20 });
      zeichnen(daten);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  function zeichnen(daten) {
    if (daten.inhalt.length === 0) {
      liste.replaceChildren(h('p', { class: 'leer' }, filter.value
        ? 'An diesem Standort ist kein Fahrzeug erfasst.'
        : 'Noch kein Fahrzeug erfasst. Lege das erste mit «Fahrzeug erfassen» an.'));
      return;
    }
    liste.replaceChildren(
      h('table', {},
        h('thead', {}, h('tr', {}, h('th', {}, 'Kennzeichen'), h('th', {}, 'Modell'), h('th', {}, 'Kategorie'),
          h('th', { class: 'zahl' }, 'km-Stand'), h('th', {}, 'Standort'), h('th', {}, 'Status'), h('th'))),
        h('tbody', {}, daten.inhalt.map((fahrzeug) => h('tr', {},
          h('td', {}, schild(fahrzeug.kennzeichen)),
          h('td', {}, fahrzeug.modell),
          h('td', {}, kategorieText(fahrzeug.kategorie)),
          h('td', { class: 'zahl' }, ganzzahl(fahrzeug.kmStand)),
          h('td', {}, fahrzeug.standortName),
          h('td', {}, fahrzeug.inBetrieb
            ? h('span', { class: 'status status-inBetrieb' }, 'In Betrieb')
            : h('span', { class: 'status status-ausserBetrieb' }, 'Ausser Betrieb')),
          h('td', { class: 'aktionen' },
            h('button', { class: 'knopf knopf-zeile knopf-klein', type: 'button',
              onclick: (e) => bearbeiten(e.currentTarget.closest('tr'), fahrzeug) }, 'Bearbeiten'),
            fahrzeug.inBetrieb
              ? knopfMitBestaetigung('Ausser Betrieb nehmen', 'Wirklich ausser Betrieb?', () => ausserBetrieb(fahrzeug))
              : null))))),
      seitenwahl(daten, laden));
  }

  function bearbeiten(zeile, fahrzeug) {
    liste.querySelector('tr.bearbeiten')?.remove();
    const neuKennzeichen = h('input', { required: true, value: fahrzeug.kennzeichen, size: 10 });
    const neuModell = h('input', { required: true, maxlength: 60, value: fahrzeug.modell });
    const neuKategorie = h('select', {}, optionen(KATEGORIEN, fahrzeug.kategorie));
    const neuStandort = h('select', {}, optionen(standorte.map((s) => [s.id, s.name]), fahrzeug.standortId));
    const formular = h('form', {
      class: 'zeile',
      onsubmit: async (e) => {
        e.preventDefault();
        try {
          // version = gelesener Stand; hat jemand anderes inzwischen gespeichert, antwortet die API mit 409
          const geaendert = await ersetzen(`/api/fahrzeuge/${fahrzeug.id}`, {
            kennzeichen: neuKennzeichen.value.trim(), modell: neuModell.value.trim(),
            kategorie: neuKategorie.value, standortId: Number(neuStandort.value), version: fahrzeug.version,
          });
          meldung(info, 'ok', `Gespeichert: ${geaendert.kennzeichen}, ${geaendert.modell} in ${geaendert.standortName}.`);
          await laden(seite);
        } catch (fehler) {
          fehlerAnzeigen(info, fehler);
        }
      },
    },
    h('label', {}, 'Kennzeichen', neuKennzeichen),
    h('label', {}, 'Modell', neuModell),
    h('label', {}, 'Kategorie', neuKategorie),
    h('label', {}, 'Standort', neuStandort),
    h('button', { class: 'knopf', type: 'submit' }, 'Speichern'),
    h('button', { class: 'knopf knopf-leise', type: 'button', onclick: () => formularZeile.remove() }, 'Abbrechen'));
    const formularZeile = h('tr', { class: 'bearbeiten' }, h('td', { colspan: 7 }, formular));
    zeile.after(formularZeile);
    neuModell.focus();
  }

  async function ausserBetrieb(fahrzeug) {
    try {
      await senden(`/api/fahrzeuge/${fahrzeug.id}/ausser-betrieb`);
      meldung(info, 'ok', `${fahrzeug.kennzeichen} ist ausser Betrieb und wird nicht mehr zur Buchung angeboten.`);
      await laden(seite);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  ziel.append(
    h('div', { class: 'abschnitt-kopf' },
      h('h1', {}, 'Fahrzeuge'),
      h('button', { class: 'knopf', type: 'button',
        onclick: () => { erfassen.hidden = false; kennzeichen.focus(); } }, 'Fahrzeug erfassen')),
    h('p', { class: 'einleitung' }, 'Standortwechsel und Ausserbetriebnahme sind nur ohne offene Reservationen möglich.'),
    erfassen,
    h('div', { class: 'zeile', style: 'margin-top:1rem' }, h('label', {}, 'Standort', filter)),
    info,
    liste);
  await laden(0);
}
