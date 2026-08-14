package com.karaka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Karaka suite service.
 *
 * <p>Two responsibilities, deliberately in one deployable while the suite is
 * this small:
 *
 * <ul>
 *   <li><b>Backend-for-frontend.</b> Runs the OpenID Connect authorization-code
 *       flow against Keycloak, holds the resulting tokens in the server-side
 *       session, and hands the browser nothing but an HttpOnly cookie.
 *   <li><b>ORBIT employee register.</b> The first real product in the suite,
 *       exposed under {@code /api} and guarded by Keycloak realm roles.
 * </ul>
 *
 * <p>Package layout follows the dependency rule — {@code orbit.domain} knows
 * nothing about Spring, {@code orbit.application} knows only the domain, and
 * only {@code orbit.web} / {@code orbit.persistence} / {@code config} touch the
 * framework. Dependencies point inward, never out.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
// Only for evicting idle rate-limiter entries; nothing here runs business logic
// on a timer.
@EnableScheduling
public class KarakaApplication {

  public static void main(String[] args) {
    SpringApplication.run(KarakaApplication.class, args);
  }
}
