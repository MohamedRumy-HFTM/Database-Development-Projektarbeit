package ch.hftm.seelandmobil.fahrzeug;

public record FahrzeugResponse(
        Long id,
        String kennzeichen,
        String modell,
        Kategorie kategorie,
        int kmStand,
        boolean inBetrieb,
        Long standortId,
        String standortName,
        long version) {

    static FahrzeugResponse von(Fahrzeug fahrzeug) {
        return new FahrzeugResponse(
                fahrzeug.getId(),
                fahrzeug.getKennzeichen(),
                fahrzeug.getModell(),
                fahrzeug.getKategorie(),
                fahrzeug.getKmStand(),
                fahrzeug.isInBetrieb(),
                fahrzeug.getStandort().getId(),
                fahrzeug.getStandort().getName(),
                fahrzeug.getVersion());
    }
}
