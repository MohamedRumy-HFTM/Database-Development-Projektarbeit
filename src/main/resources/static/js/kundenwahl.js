// Kunden suchen und auswählen (Buchen, Reservationen). Bei 20'000 Kunden nur über die Suche.

import { lesen } from './api.js';
import { h } from './dom.js';

export function kundenwahl({ beschriftung = 'Kunde', vorauswahl = null, gewaehlt: beiWahl }) {
  let aktuell = null;
  let zeitgeber;
  const feld = h('input', { type: 'search', placeholder: 'Name oder E-Mail', autocomplete: 'off' });
  const liste = h('ul', { class: 'treffer' });
  const anzeige = h('div');
  const suche = h('label', {}, beschriftung, feld);
  const wurzel = h('div', { class: 'kundenwahl' }, suche, liste, anzeige);

  async function suchen() {
    const text = feld.value.trim();
    if (text.length < 2) { liste.replaceChildren(); return; }
    const seite = await lesen('/api/kunden', { suche: text, groesse: 8 });
    liste.replaceChildren(...(seite.inhalt.length
      ? seite.inhalt.map((kunde) => h('li', {}, h('button', { type: 'button', onclick: () => waehlen(kunde) },
          h('span', {}, kunde.name, kunde.gesperrt ? ' (gesperrt)' : ''), h('small', {}, kunde.email))))
      : [h('li', { class: 'leer' }, 'Kein Kunde gefunden.')]));
  }

  function waehlen(kunde) {
    aktuell = kunde;
    liste.replaceChildren();
    suche.hidden = true;
    anzeige.replaceChildren(h('div', { class: 'gewaehlt' },
      h('strong', {}, kunde.name),
      h('span', {}, kunde.email),
      kunde.gesperrt ? h('span', { class: 'status status-gesperrt' }, 'Gesperrt') : null,
      h('button', { class: 'knopf knopf-leise knopf-klein', type: 'button', onclick: zuruecksetzen }, 'Anderen Kunden wählen')));
    beiWahl?.(kunde);
  }

  function zuruecksetzen() {
    aktuell = null;
    anzeige.replaceChildren();
    suche.hidden = false;
    feld.value = '';
    feld.focus();
    beiWahl?.(null);
  }

  feld.addEventListener('input', () => {
    clearTimeout(zeitgeber);
    zeitgeber = setTimeout(suchen, 250);
  });

  if (vorauswahl) waehlen(vorauswahl);

  return { element: wurzel, kunde: () => aktuell, waehlen };
}
