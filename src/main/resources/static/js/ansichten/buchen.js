// A6 freie Fahrzeuge suchen, A3 reservieren, und die Konflikt-Demo mit zwei gleichzeitigen Anfragen.

import { alleStandorte, lesen, senden } from '../api.js';
import {
  KATEGORIEN, alsIso, fehlerAnzeigen, fuerEingabe, h, kategorieText, meldung, meldungsbereich, optionen,
  schild, seitenwahl, zeitraum,
} from '../dom.js';
import { kundenwahl } from '../kundenwahl.js';

export async function anzeigen(ziel) {
  const standorte = await alleStandorte();
  const morgen = new Date();
  morgen.setDate(morgen.getDate() + 1);
  morgen.setHours(10, 0, 0, 0);
  const morgenEnde = new Date(morgen);
  morgenEnde.setHours(14);

  const info = meldungsbereich();
  let ergebnis = null;

  const kunde = kundenwahl({ beschriftung: 'Kunde', gewaehlt: () => { zeichnen(); demo.pruefen(); } });
  const beginn = h('input', { type: 'datetime-local', value: fuerEingabe(morgen), required: true });
  const ende = h('input', { type: 'datetime-local', value: fuerEingabe(morgenEnde), required: true });
  const standort = h('select', {}, h('option', { value: '' }, 'Alle Standorte'),
    optionen(standorte.map((s) => [s.id, s.name])));
  const kategorie = h('select', {}, h('option', { value: '' }, 'Alle Kategorien'), optionen(KATEGORIEN));
  const liste = h('div', { class: 'rahmen' });

  const suche = h('form', { class: 'zeile', onsubmit: (e) => { e.preventDefault(); info.hidden = true; suchen(0); } },
    h('label', {}, 'Beginn', beginn),
    h('label', {}, 'Ende', ende),
    h('label', {}, 'Standort', standort),
    h('label', {}, 'Kategorie', kategorie),
    h('button', { class: 'knopf', type: 'submit' }, 'Freie Fahrzeuge suchen'));

  async function suchen(seite) {
    try {
      const von = alsIso(beginn.value);
      const bis = alsIso(ende.value);
      const daten = await lesen('/api/fahrzeuge/verfuegbar',
        { von, bis, standortId: standort.value, kategorie: kategorie.value, seite, groesse: 10 });
      ergebnis = { von, bis, daten };
      zeichnen();
      demo.fahrzeugeSetzen(daten.inhalt, von, bis);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  function zeichnen() {
    if (!ergebnis) return;
    const gewaehlt = kunde.kunde();
    const { daten } = ergebnis;
    if (daten.inhalt.length === 0) {
      liste.replaceChildren(h('p', { class: 'leer' }, 'In diesem Zeitraum ist kein passendes Fahrzeug frei.'));
      return;
    }
    liste.replaceChildren(
      h('p', { class: 'klein' }, `${daten.anzahlGesamt} freie Fahrzeuge, ${zeitraum(ergebnis.von, ergebnis.bis)}`),
      h('table', {},
        h('thead', {}, h('tr', {}, h('th', {}, 'Kennzeichen'), h('th', {}, 'Modell'), h('th', {}, 'Kategorie'),
          h('th', {}, 'Standort'), h('th'))),
        h('tbody', {}, daten.inhalt.map((fahrzeug) => h('tr', {},
          h('td', {}, schild(fahrzeug.kennzeichen)),
          h('td', {}, fahrzeug.modell),
          h('td', {}, kategorieText(fahrzeug.kategorie)),
          h('td', {}, fahrzeug.standortName),
          h('td', { class: 'aktionen' }, h('button', {
            class: 'knopf knopf-zeile knopf-klein', type: 'button', disabled: !gewaehlt,
            title: gewaehlt ? null : 'Zuerst einen Kunden wählen',
            onclick: () => reservieren(fahrzeug),
          }, 'Reservieren')))))),
      seitenwahl(daten, suchen));
  }

  async function reservieren(fahrzeug) {
    const gewaehlt = kunde.kunde();
    try {
      const reservation = await senden('/api/reservationen',
        { kundeId: gewaehlt.id, fahrzeugId: fahrzeug.id, beginn: ergebnis.von, ende: ergebnis.bis });
      meldung(info, 'ok', `Reserviert: ${reservation.kennzeichen} für ${gewaehlt.name}, `
        + `${zeitraum(reservation.beginn, reservation.ende)}, Abholung in ${reservation.standortName}.`);
      await suchen(ergebnis.daten.seite);
    } catch (fehler) {
      fehlerAnzeigen(info, fehler);
    }
  }

  const demo = konfliktDemo(() => kunde.kunde(), () => suchen(ergebnis?.daten.seite ?? 0));

  ziel.append(
    h('h1', {}, 'Fahrzeug buchen'),
    h('p', { class: 'einleitung' }, 'Freie Fahrzeuge für einen Zeitraum suchen und für einen Kunden reservieren.'),
    kunde.element,
    h('h2', {}, 'Zeitraum'),
    suche,
    info,
    liste,
    demo.element);
}

/**
 * Zwei Anfragen für dasselbe Fahrzeug mit überlappenden Zeiten, gleichzeitig abgeschickt.
 * Der Exclusion-Constraint lässt nur eine zu; welche gewinnt, entscheidet die Reihenfolge in der Datenbank.
 */
function konfliktDemo(kundeA, nachher) {
  let von;
  let bis;
  const fahrzeug = h('select', { disabled: true }, h('option', { value: '' }, 'Zuerst freie Fahrzeuge suchen'));
  const kundeB = kundenwahl({ beschriftung: 'Zweiter Kunde (Anfrage B, optional)', gewaehlt: () => pruefen() });
  const zeiten = h('p', { class: 'klein' });
  const start = h('button', { class: 'knopf', type: 'button', disabled: true, onclick: senden2 },
    'Beide Anfragen gleichzeitig senden');
  const bahnA = h('div', { class: 'bahn' });
  const bahnB = h('div', { class: 'bahn' });
  const bahnen = h('div', { class: 'bahnen', hidden: true }, bahnA, bahnB);

  function verschoben(iso, millis) {
    return new Date(new Date(iso).getTime() + millis).toISOString();
  }

  function zeitraumB() {
    const halbe = (new Date(bis) - new Date(von)) / 2;
    return [verschoben(von, halbe), verschoben(bis, halbe)];
  }

  function fahrzeugeSetzen(fahrzeuge, neuVon, neuBis) {
    von = neuVon;
    bis = neuBis;
    fahrzeug.replaceChildren(...(fahrzeuge.length
      ? fahrzeuge.map((f) => h('option', { value: f.id }, `${f.kennzeichen}, ${f.modell}`))
      : [h('option', { value: '' }, 'Kein freies Fahrzeug')]));
    fahrzeug.disabled = fahrzeuge.length === 0;
    const [vonB, bisB] = zeitraumB();
    zeiten.textContent = `Anfrage A: ${zeitraum(von, bis)}. Anfrage B: ${zeitraum(vonB, bisB)}.`;
    pruefen();
  }

  function pruefen() {
    start.disabled = !kundeA() || !fahrzeug.value;
  }

  function bahnZeichnen(bahn, titel, kunde, zeitraumText, zustand) {
    bahn.className = `bahn ${zustand.klasse ?? 'bahn-wartet'}`;
    bahn.replaceChildren(
      h('h3', {}, titel),
      h('p', {}, kunde.name),
      h('p', { class: 'klein' }, zeitraumText),
      h('p', { class: 'bahn-code' }, zustand.code ?? '…'),
      h('p', {}, zustand.text ?? 'Gesendet, wartet auf Antwort'),
      zustand.ms !== undefined ? h('p', { class: 'klein' }, `Antwort nach ${zustand.ms} ms`) : null);
  }

  async function eineAnfrage(kunde, beginn, ende) {
    const t0 = performance.now();
    try {
      await senden('/api/reservationen', { kundeId: kunde.id, fahrzeugId: Number(fahrzeug.value), beginn, ende });
      return { klasse: 'bahn-ok', code: 201, text: 'Reserviert', ms: Math.round(performance.now() - t0) };
    } catch (fehler) {
      return { klasse: 'bahn-fehler', code: fehler.status ?? '–', text: `Abgelehnt: ${fehler.message}`,
        ms: Math.round(performance.now() - t0) };
    }
  }

  async function senden2() {
    const a = kundeA();
    const b = kundeB.kunde() ?? a;
    const [vonB, bisB] = zeitraumB();
    const textA = zeitraum(von, bis);
    const textB = zeitraum(vonB, bisB);
    bahnen.hidden = false;
    start.disabled = true;
    bahnZeichnen(bahnA, 'Anfrage A', a, textA, {});
    bahnZeichnen(bahnB, 'Anfrage B', b, textB, {});
    const [ergebnisA, ergebnisB] = await Promise.all([eineAnfrage(a, von, bis), eineAnfrage(b, vonB, bisB)]);
    bahnZeichnen(bahnA, 'Anfrage A', a, textA, ergebnisA);
    bahnZeichnen(bahnB, 'Anfrage B', b, textB, ergebnisB);
    await nachher();
  }

  fahrzeug.addEventListener('change', pruefen);

  const element = h('section', {},
    h('h2', {}, 'Gleichzeitig buchen'),
    h('p', { class: 'einleitung' }, 'Zwei Anfragen für dasselbe Fahrzeug mit überlappenden Zeiten gehen gleichzeitig '
      + 'an den Server. Die Datenbank lässt nur eine zu, die andere erhält 409. Anfrage A bucht für den oben gewählten Kunden.'),
    h('div', { class: 'zeile' }, h('label', {}, 'Fahrzeug', fahrzeug)),
    zeiten,
    kundeB.element,
    h('p', {}, start),
    bahnen);

  return { element, fahrzeugeSetzen, pruefen };
}
