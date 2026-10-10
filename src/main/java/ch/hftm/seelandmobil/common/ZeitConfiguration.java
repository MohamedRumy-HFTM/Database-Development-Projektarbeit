package ch.hftm.seelandmobil.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Aktuelle Zeit als Bean, damit Regeln wie «Beginn nicht in der Vergangenheit» testbar sind. */
@Configuration
public class ZeitConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
