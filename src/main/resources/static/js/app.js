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
let aktuelleAnzeige = 0;

async function anzeigen() {
  const nummer = ++aktuelleAnzeige;
  const [pfad, query = ''] = location.hash.replace(/^#\/?/, '').split('?');
  const name = ansichten[pfad] ? pfad : 'buchen';
  for (const link of document.querySelectorAll('.leiste nav a')) {
    if (link.getAttribute('href') === `#/${name}`) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  }
  // Jede Anzeige bekommt einen eigenen Behälter: Wechselt man schnell weiter, schreibt eine
  // überholte Ansicht nur noch in ihren eigenen, nicht mehr sichtbaren Behälter.
  const behaelter = h('div');
  try {
    const modul = await ansichten[name]();
    if (nummer !== aktuelleAnzeige) return;
    inhalt.replaceChildren(behaelter);
    await modul.anzeigen(behaelter, new URLSearchParams(query));
  } catch (fehler) {
    if (nummer !== aktuelleAnzeige) return;
    inhalt.replaceChildren(h('div', { class: 'meldung meldung-fehler' },
      h('p', {}, `Die Ansicht konnte nicht geladen werden: ${fehler.message}`)));
  }
}

window.addEventListener('hashchange', anzeigen);
anzeigen();
