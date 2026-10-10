// Zugriff auf die REST-API. Fehler kommen als Problem Details (RFC 9457) und werden zu ApiFehler.

export class ApiFehler extends Error {
  constructor(status, problem) {
    super(problem?.detail ?? `Anfrage fehlgeschlagen (${status})`);
    this.status = status;
    this.fehler = problem?.fehler ?? {};
  }
}

function mitParametern(pfad, parameter = {}) {
  const query = new URLSearchParams();
  for (const [name, wert] of Object.entries(parameter)) {
    if (wert !== undefined && wert !== null && wert !== '') {
      query.set(name, wert);
    }
  }
  const text = query.toString();
  return text ? `${pfad}?${text}` : pfad;
}

async function anfrage(methode, pfad, body, mitKopf = false) {
  const antwort = await fetch(pfad, {
    method: methode,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await antwort.text();
  const daten = text ? JSON.parse(text) : null;
  if (!antwort.ok) {
    throw new ApiFehler(antwort.status, daten);
  }
  return mitKopf ? { daten, kopf: antwort.headers } : daten;
}

export const lesen = (pfad, parameter) => anfrage('GET', mitParametern(pfad, parameter));
export const senden = (pfad, body) => anfrage('POST', pfad, body);
export const ersetzen = (pfad, body) => anfrage('PUT', pfad, body);

/** Wie lesen, liefert zusätzlich die Zeit im Server aus dem Header Server-Timing (ms, sonst null). */
export async function lesenMitServerzeit(pfad, parameter) {
  const { daten, kopf } = await anfrage('GET', mitParametern(pfad, parameter), undefined, true);
  const dauer = /dur=([\d.]+)/.exec(kopf.get('Server-Timing') ?? '');
  return { daten, serverMs: dauer ? Number(dauer[1]) : null };
}

let standorte;

/** Standorte ändern sich nicht (Migration V6) und werden nur einmal geladen. */
export async function alleStandorte() {
  standorte ??= await lesen('/api/standorte');
  return standorte;
}
