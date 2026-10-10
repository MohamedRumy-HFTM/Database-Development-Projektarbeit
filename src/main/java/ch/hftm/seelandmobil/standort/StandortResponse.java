package ch.hftm.seelandmobil.standort;

public record StandortResponse(Long id, String name, String ort) {

    static StandortResponse von(Standort standort) {
        return new StandortResponse(standort.getId(), standort.getName(), standort.getOrt());
    }
}
