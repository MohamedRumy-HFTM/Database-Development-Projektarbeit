package ch.hftm.seelandmobil.common;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Einfacher In-Memory-Cache (ConcurrentHashMap), siehe spring.cache in application.yml. */
@Configuration
@EnableCaching
public class CacheConfiguration {
}
