package com.karaka.config;

import com.karaka.model.enums.SuiteProduct;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.karaka.config.secure.BrowserAccessDeniedHandler;

/**
 * Clean URLs for the static pages.
 *
 * <p>Spring's resource handler serves {@code classpath:/static/} but maps a welcome
 * page only for {@code /} — a request for {@code /aura} would 404 even though the
 * file exists. Forwarding keeps the address bar on {@code /aura} rather than exposing
 * {@code /index.html}, and a forward is re-resolved internally with no second HTTP
 * round trip.
 *
 * <p>All four products forward to the <em>same</em> page. This release is a Keycloak
 * access demo, not four products: the page reads the product from
 * {@code location.pathname} and renders that product's capabilities. Giving each a
 * real UI later means adding {@code static/aura/index.html} and pointing its route
 * at it — the route list here does not change shape.
 *
 * <p>Routes are generated from {@link SuiteProduct}, so adding an enum constant adds
 * its route. A hand-maintained list is how one product ends up reachable and another
 * silently 404s.
 */
@Configuration
public class WebRoutingConfig implements WebMvcConfigurer {

  private static final String PRODUCT_VIEW = "forward:/product/index.html";

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // Redirect rather than forward, so an unauthenticated visitor's saved request is
    // /picker and login returns them somewhere real instead of to a redirect.
    registry.addRedirectViewController("/", "/picker");
    registry.addViewController("/picker").setViewName("forward:/picker/index.html");
    registry.addRedirectViewController("/picker/", "/picker");

    // Where BrowserAccessDeniedHandler sends a refused navigation. Reachable by any
    // signed-in user by definition — it is what someone sees precisely when they
    // lack the entitlement for somewhere else.
    registry.addViewController("/no-access").setViewName("forward:/no-access/index.html");

    // Where oauth2Login's failureHandler sends a broken exchange. Public by
    // necessity: there is no session at this point, and requiring one would bounce
    // the user back into the flow that just failed.
    registry.addViewController("/sign-in-failed").setViewName("forward:/sign-in-failed/index.html");

    // Reached from the Keycloak login page's "Forgot Password?" link. Owned by the
    // application rather than Keycloak because it reports whether the account exists,
    // which Keycloak's own reset page deliberately refuses to disclose.
    registry.addViewController("/forgot-password")
        .setViewName("forward:/forgot-password/index.html");
    registry.addRedirectViewController("/forgot-password/", "/forgot-password");

    for (SuiteProduct product : SuiteProduct.values()) {
      registry.addViewController(product.path()).setViewName(PRODUCT_VIEW);
      // Spring 6 stopped matching trailing slashes automatically, and someone
      // typing /aura/ should not get a 404.
      registry.addRedirectViewController(product.path() + "/", product.path());
    }
  }
}
