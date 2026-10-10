// Kleine Helfer für die Oberfläche. Texte werden immer als Text gesetzt, nie als HTML.

export function h(tag, eigenschaften = {}, ...kinder) {
  const element = document.createElement(tag);
  for (const [name, wert] of Object.entries(eigenschaften ?? {})) {
    if (wert === undefined || wert === null || wert === false) continue;
    if (name.startsWith('on')) {
      element.addEventListener(name.slice(2).toLowerCase(), wert);
    } else if (name === 'class') {
      element.className = wert;
    } else if (name in element && typeof wert !== 'string' && typeof wert !== 'number') {
      // Wahrheitswerte als Eigenschaft (disabled, hidden, selected); Zahlen und Texte als Attribut,
      // damit form.reset() auf diese Werte zurückstellt
      element[name] = wert;
    } else {
      element.setAttribute(name, wert === true ? '' : wert);
    }
  }
  for (const kind of kinder.flat()) {
    if (kind === undefined || kind === null || kind === false) continue;
    element.append(kind instanceof Node ? kind : String(kind));
  }
  return element;
}

export const schild = (kennzeichen, gross = false) =>
  h('span', { class: gross ? 'schild schild-gross' : 'schild' }, kennzeichen);

const datumFormat = new Intl.DateTimeFormat('de-CH', {
  weekday: 'short', day: 'numeric', month: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit',
});
const zeitFormat = new Intl.DateTimeFormat('de-CH', { hour: '2-digit', minute: '2-digit' });
const zahlFormat = new Intl.NumberFormat('de-CH');

export const datumZeit = (iso) => datumFormat.format(new Date(iso));
export const zahl = (wert, stellen = 0) =>
  new Intl.NumberFormat('de-CH', { minimumFractionDigits: stellen, maximumFractionDigits: stellen }).format(wert);
export const ganzzahl = (wert) => zahlFormat.format(wert);

/** «Mi., 1.9.2027, 10:00–14:00» oder über mehrere Tage beide Zeitpunkte ganz. */
export function zeitraum(beginnIso, endeIso) {
  const beginn = new Date(beginnIso);
  const ende = new Date(endeIso);
  return beginn.toDateString() === ende.toDateString()
    ? `${datumFormat.format(beginn)}–${zeitFormat.format(ende)}`
    : `${datumFormat.format(beginn)} bis ${datumFormat.format(ende)}`;
}

/** Wert eines datetime-local-Felds (Ortszeit) als ISO-Zeitpunkt. */
export const alsIso = (eingabe) => new Date(eingabe).toISOString();

/** Date als Wert für ein datetime-local-Feld (Ortszeit, Minuten). */
export function fuerEingabe(datum) {
  const zweistellig = (n) => String(n).padStart(2, '0');
  return `${datum.getFullYear()}-${zweistellig(datum.getMonth() + 1)}-${zweistellig(datum.getDate())}`
    + `T${zweistellig(datum.getHours())}:${zweistellig(datum.getMinutes())}`;
}

/** Erfolgs- oder Fehlermeldung in einem Bereich mit aria-live. */
export function meldung(bereich, art, text, fehler = {}) {
  bereich.replaceChildren();
  bereich.hidden = false;
  bereich.className = `meldung meldung-${art}`;
  bereich.append(h('p', {}, text));
  const felder = Object.entries(fehler);
  if (felder.length) {
    bereich.append(h('ul', {}, felder.map(([feld, grund]) => h('li', {}, `${feld}: ${grund}`))));
  }
}

export function meldungsbereich() {
  return h('div', { class: 'meldung', hidden: true, role: 'status', 'aria-live': 'polite' });
}

/** Zeigt einen ApiFehler oder einen unerwarteten Fehler an. */
export function fehlerAnzeigen(bereich, fehler) {
  meldung(bereich, 'fehler', fehler.message, fehler.fehler);
}

/** Seitenwahl unter einer Liste. seite beginnt bei 0. */
export function seitenwahl(daten, wechseln) {
  if (daten.anzahlSeiten <= 1) return h('div');
  return h('div', { class: 'seiten' },
    h('button', { class: 'knopf knopf-leise knopf-klein', type: 'button', disabled: daten.seite === 0,
      onclick: () => wechseln(daten.seite - 1) }, 'Zurück'),
    h('span', {}, `Seite ${daten.seite + 1} von ${ganzzahl(daten.anzahlSeiten)} (${ganzzahl(daten.anzahlGesamt)} Einträge)`),
    h('button', { class: 'knopf knopf-leise knopf-klein', type: 'button',
      disabled: daten.seite + 1 >= daten.anzahlSeiten, onclick: () => wechseln(daten.seite + 1) }, 'Weiter'));
}

/** Knopf, der erst beim zweiten Klick ausführt (für Schritte ohne Rückweg). */
export function knopfMitBestaetigung(text, bestaetigung, ausfuehren) {
  let scharf = false;
  let zeitgeber;
  const ruhig = 'knopf knopf-leise knopf-klein';
  const knopf = h('button', { class: ruhig, type: 'button' }, text);
  knopf.addEventListener('click', async () => {
    if (!scharf) {
      scharf = true;
      knopf.textContent = bestaetigung;
      knopf.className = 'knopf knopf-warn knopf-klein';
      zeitgeber = setTimeout(() => { scharf = false; knopf.textContent = text; knopf.className = ruhig; }, 4000);
      return;
    }
    clearTimeout(zeitgeber);
    knopf.disabled = true;
    await ausfuehren();
  });
  return knopf;
}

export function optionen(eintraege, gewaehlt) {
  return eintraege.map(([wert, text]) => h('option', { value: wert, selected: String(wert) === String(gewaehlt) }, text));
}

export const KATEGORIEN = [['KLEIN', 'Klein'], ['KOMBI', 'Kombi'], ['TRANSPORTER', 'Transporter']];
export const kategorieText = (wert) => KATEGORIEN.find(([k]) => k === wert)?.[1] ?? wert;
