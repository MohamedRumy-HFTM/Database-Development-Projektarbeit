package ch.hftm.seelandmobil.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Eine Seite eines Suchergebnisses. seite beginnt bei 0. */
public record Seite<T>(List<T> inhalt, int seite, int groesse, long anzahlGesamt, int anzahlSeiten) {

    public static <T> Seite<T> von(Page<T> page) {
        return new Seite<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
