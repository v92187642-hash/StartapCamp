package ru.otmetka.core.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TimeConfig {

    /** Inject this instead of calling Instant.now(), so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
