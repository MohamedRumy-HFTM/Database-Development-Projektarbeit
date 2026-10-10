package ch.hftm.seelandmobil.kunde;

public record KundeResponse(Long id, String name, String email, boolean gesperrt) {

    static KundeResponse von(Kunde kunde) {
        return new KundeResponse(kunde.getId(), kunde.getName(), kunde.getEmail(), kunde.isGesperrt());
    }
}
