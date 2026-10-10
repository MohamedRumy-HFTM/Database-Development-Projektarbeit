// Einstieg: wählt anhand der Adresse (#/bereich?param=wert) die Ansicht.

import { h } from './dom.js';

const ansichten = {
  buchen: () => import('./ansichten/buchen.js'),
  reservationen: () => import('./ansichten/reservationen.js'),
  fahrzeuge: () => import('./ansichten/fahrzeuge.js'),
  kunden: () => import('./ansichten/kunden.js'),
  auswertung: () => import('./ansichten/auswertung.js'),
};

const inhalt = document.getElementById('inhalt');

async function anzeigen() {
  const [pfad, query = ''] = location.hash.replace(/^#\/?/, '').split('?');
  const name = ansichten[pfad] ? pfad : 'buchen';
  for (const link of document.querySelectorAll('.leiste nav a')) {
    if (link.getAttribute('href') === `#/${name}`) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  }
  try {
    const modul = await ansichten[name]();
    inhalt.replaceChildren();
    await modul.anzeigen(inhalt, new URLSearchParams(query));
  } catch (fehler) {
    inhalt.replaceChildren(h('div', { class: 'meldung meldung-fehler' },
      h('p', {}, `Die Ansicht konnte nicht geladen werden: ${fehler.message}`)));
  }
}

window.addEventListener('hashchange', anzeigen);
anzeigen();
