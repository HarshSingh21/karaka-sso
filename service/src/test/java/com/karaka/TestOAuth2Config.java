package com.karaka;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Supplies the OAuth2 client registration so tests need no Keycloak.
 *
 * <p>Necessary because Spring Boot builds the registration from {@code issuer-uri} by
 * calling the provider's discovery document <em>at startup</em>. Blanking the property
 * does not help — an empty value is validated and rejected with
 * {@code issuer cannot be empty}, not treated as absent.
 *
 * <p>Defining the bean here is what stops that: Boot's own
 * {@code ClientRegistrationRepositoryConfiguration} is
 * {@code @ConditionalOnMissingBean}, so it backs off and no discovery call is made.
 *
 * <p>The endpoints point at port 0 deliberately. No test performs a real login, so any
 * request to them would be a bug — and a connection refused is a much clearer signal
 * than a call that quietly succeeds against something real.
 */
@TestConfiguration
public class TestOAuth2Config {

  @Bean
  ClientRegistrationRepository clientRegistrationRepository() {
    return new InMemoryClientRegistrationRepository(
        ClientRegistration.withRegistrationId("keycloak")
            .clientId("karaka-web")
            .clientSecret("test-only-not-a-real-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .issuerUri("http://localhost:0/realms/test")
            .authorizationUri("http://localhost:0/realms/test/protocol/openid-connect/auth")
            .tokenUri("http://localhost:0/realms/test/protocol/openid-connect/token")
            .jwkSetUri("http://localhost:0/realms/test/protocol/openid-connect/certs")
            .userInfoUri("http://localhost:0/realms/test/protocol/openid-connect/userinfo")
            .userNameAttributeName("preferred_username")
            .clientName("Karaka test")
            .build());
  }
}
