// A2 Kunden erfassen, ändern und sperren.

import { ersetzen, lesen, senden } from '../api.js';
import { fehlerAnzeigen, h, knopfMitBestaetigung, meldung, meldungsbereich, seitenwahl } from '../dom.js';

export async function anzeigen(ziel) {
  const info = meldungsbereich();
  const liste = h('div', { class: 'rahmen' });
  const suche = h('input', { type: 'search', placeholder: 'Name, E-Mail oder Nummer', autocomplete: 'off' });
  let seite = 0;
  let zeitgeber;

  const name = h('input', { required: true, maxlength: 100, size: 24 });
  const email = h('input', { type: 'email', required: true, maxlength: 254, size: 28 });
  const erfassen = h('form', {
    class: 'zeile', hidden: true,
    onsubmit: async (e) => {
      e.preventDefault();
      try {
        const neu = await senden('/api/kunden', { name: name.value.trim(), email: email.value.trim() });
        meldung(info, 'ok', `Erfasst: ${neu.name}, ${neu.email}.`);
        erfassen.reset();
        erfassen.hidden = true;
        suche.value = neu.name;
        await laden(0);
      } catch (fehler) {
        fehlerAnzeigen(info, fehler);
      }
    },
  },
  h('label', {}, 'Name', name),
  h('label', {}, 'E-Mail', email),
  h('button', { class: 'knopf', type: 'submit' }, 'Kunde erfassen'),
  h('button', { class: 'knopf knopf-leise', type: 'button', onclick: () => { erfassen.hidden = true; } }, 'Abbrechen'));

  async function laden(neueSeite) {
    seite = neueSeite;
    try {
      const daten = await lesen('/api/kunden', { suche: suche.value.trim(), seite, groesse: 20 });
      zeichnen(daten);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  function zeichnen(daten) {
    if (daten.inhalt.length === 0) {
      liste.replaceChildren(h('p', { class: 'leer' }, suche.value.trim()
        ? 'Kein Kunde gefunden. Prüfe die Schreibweise oder erfasse einen neuen Kunden.'
        : 'Noch kein Kunde erfasst. Lege den ersten mit «Kunde erfassen» an.'));
      return;
    }
    liste.replaceChildren(
      h('table', {},
        h('thead', {}, h('tr', {}, h('th', {}, 'Name'), h('th', {}, 'E-Mail'), h('th', {}, 'Status'), h('th'))),
        h('tbody', {}, daten.inhalt.map((kunde) => h('tr', {},
          h('td', {}, kunde.name),
          h('td', {}, kunde.email),
          h('td', {}, kunde.gesperrt ? h('span', { class: 'status status-gesperrt' }, 'Gesperrt') : null),
          h('td', { class: 'aktionen' },
            h('a', { class: 'knopf knopf-leise knopf-klein', href: `#/reservationen?kunde=${kunde.id}`,
              style: 'text-decoration:none;display:inline-block' }, 'Reservationen'),
            ' ',
            h('button', { class: 'knopf knopf-zeile knopf-klein', type: 'button',
              onclick: (e) => bearbeiten(e.currentTarget.closest('tr'), kunde) }, 'Bearbeiten'),
            kunde.gesperrt ? null : ' ',
            kunde.gesperrt ? null : knopfMitBestaetigung('Sperren', 'Wirklich sperren?', () => sperren(kunde))))))),
      seitenwahl(daten, laden));
  }

  function bearbeiten(zeile, kunde) {
    liste.querySelector('tr.bearbeiten')?.remove();
    const neuName = h('input', { required: true, maxlength: 100, value: kunde.name, size: 24 });
    const neuEmail = h('input', { type: 'email', required: true, maxlength: 254, value: kunde.email, size: 28 });
    const formular = h('form', {
      class: 'zeile',
      onsubmit: async (e) => {
        e.preventDefault();
        try {
          const geaendert = await ersetzen(`/api/kunden/${kunde.id}`, { name: neuName.value.trim(), email: neuEmail.value.trim() });
          meldung(info, 'ok', `Gespeichert: ${geaendert.name}, ${geaendert.email}.`);
          await laden(seite);
        } catch (fehler) {
          fehlerAnzeigen(info, fehler);
        }
      },
    },
    h('label', {}, 'Name', neuName),
    h('label', {}, 'E-Mail', neuEmail),
    h('button', { class: 'knopf', type: 'submit' }, 'Speichern'),
    h('button', { class: 'knopf knopf-leise', type: 'button', onclick: () => formularZeile.remove() }, 'Abbrechen'));
    const formularZeile = h('tr', { class: 'bearbeiten' }, h('td', { colspan: 4 }, formular));
    zeile.after(formularZeile);
    neuName.focus();
  }

  async function sperren(kunde) {
    try {
      await senden(`/api/kunden/${kunde.id}/sperren`);
      meldung(info, 'ok', `${kunde.name} ist gesperrt und kann keine neuen Reservationen mehr anlegen.`);
      await laden(seite);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  suche.addEventListener('input', () => {
    clearTimeout(zeitgeber);
    zeitgeber = setTimeout(() => { info.hidden = true; laden(0); }, 250);
  });

  ziel.append(
    h('div', { class: 'abschnitt-kopf' },
      h('h1', {}, 'Kunden'),
      h('button', { class: 'knopf', type: 'button',
        onclick: () => { erfassen.hidden = false; name.focus(); } }, 'Kunde erfassen')),
    h('p', { class: 'einleitung' }, 'Gesperrte Kunden können keine neuen Reservationen anlegen. Die E-Mail-Adresse ist eindeutig, unabhängig von Gross- und Kleinschreibung.'),
    erfassen,
    h('div', { class: 'zeile', style: 'margin-top:1rem' }, h('label', { style: 'min-width:20rem' }, 'Suche', suche)),
    info,
    liste);
  await laden(0);
}
