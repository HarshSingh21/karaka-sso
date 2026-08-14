package com.karaka.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the system clock as a bean.
 *
 * <p>Nothing in the application or domain layer calls {@code Instant.now()} or
 * {@code LocalDate.now()} directly — they take this {@link Clock} instead. That
 * is what lets a test pin "today" to a fixed date and assert on join dates,
 * exit dates and audit timestamps without sleeping or tolerating drift.
 */
@Configuration
public class TimeConfig {

  @Bean
  Clock systemClock() {
    return Clock.systemDefaultZone();
  }
}
