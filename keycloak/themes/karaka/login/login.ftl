<#--
  Karaka sign-in.

  The page chrome (shell, hero, brand row, card, alert) lives in karaka-layout.ftl and is
  shared with the reset-password, update-password, info and error screens. This file holds
  only what is specific to signing in.
-->
<#import "karaka-layout.ftl" as k>

<#--
  Brand marks, inlined as SVG.

  Inline rather than linked files: the login page must render with no network fetch beyond
  its own stylesheet, and a missing icon on a sign-in button reads as a broken page.

  Google's mark is its four official brand colours on a WHITE ground — that is a
  requirement of Google's identity guidelines, not a style choice, which is why
  .social-btn sets #fff explicitly instead of using the theme's lavender surface.
  X's mark is monochrome and inherits currentColor, so it follows the text.

  Providers without a mark here fall back to a lettered tile rather than a broken or
  approximated logo — an inaccurate brand mark is worse than none.
-->
<#macro providerMark alias>
  <#if alias == "google">
    <svg class="social-mark" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
      <path fill="#4285F4" d="M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11c4.16-3.83 6.56-9.47 6.56-16.17z"/>
      <path fill="#34A853" d="M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7C7.96 41.07 15.4 46 24 46z"/>
      <path fill="#FBBC05" d="M11.69 28.18C11.25 26.86 11 25.45 11 24s.25-2.86.69-4.18v-5.7H4.34C2.85 17.09 2 20.45 2 24s.85 6.91 2.34 9.88l7.35-5.7z"/>
      <path fill="#EA4335" d="M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z"/>
    </svg>
  <#elseif alias == "twitter" || alias == "x">
    <svg class="social-mark" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
      <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
    </svg>
  <#elseif alias == "microsoft">
    <svg class="social-mark" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <rect x="1"  y="1"  width="10" height="10" fill="#F25022"/>
      <rect x="13" y="1"  width="10" height="10" fill="#7FBA00"/>
      <rect x="1"  y="13" width="10" height="10" fill="#00A4EF"/>
      <rect x="13" y="13" width="10" height="10" fill="#FFB900"/>
    </svg>
  <#else>
    <span class="social-mark social-mark-letter" aria-hidden="true">${alias?upper_case?substring(0,1)}</span>
  </#if>
</#macro>

<@k.page pageTitle=msg("doLogIn") heading=msg("doLogIn")
         subtitle="Use your work email to continue." wrapClass="form-wrap-signin">

  <#if realm.password>
  <form class="form" action="${url.loginAction}" method="post" novalidate>
    <div class="field-float">
      <input class="input-float" id="username" name="username" type="text"
             placeholder=" " autofocus autocomplete="username"
             value="${(login.username!'')}"
             aria-invalid="${messagesPerField.existsError('username','password')?c}">
      <label for="username">
        <#if !realm.loginWithEmailAllowed>${msg("username")}<#else>${msg("email")}</#if>
      </label>
      <#if messagesPerField.existsError('username','password')>
        <div class="field-error" aria-live="polite">
          ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
        </div>
      </#if>
    </div>

    <div class="field-float">
      <input class="input-float" id="password" name="password" type="password"
             placeholder=" " autocomplete="current-password"
             aria-invalid="${messagesPerField.existsError('username','password')?c}">
      <label for="password">${msg("password")}</label>
    </div>

    <#if realm.rememberMe || realm.resetPasswordAllowed>
      <div class="form-aside">
        <#if realm.rememberMe>
          <label class="remember">
            <input type="checkbox" name="rememberMe" <#if login.rememberMe??>checked</#if>>
            ${msg("rememberMe")}
          </label>
        <#else>
          <span></span>
        </#if>
        <#-- Points at the APPLICATION's reset page, not Keycloak's.
             Keycloak's own page cannot say "no such account" — its Choose User authenticator
             is configurable=false and answers identically either way. The application owns
             this flow so it can tell the user plainly, and it still delegates the actual
             password change back to Keycloak via an action-token link.
             client.baseUrl comes from the client's baseUrl in the realm; if it is unset we
             fall back to Keycloak's page rather than rendering a broken relative link. -->
        <#if realm.resetPasswordAllowed>
          <#if client?? && client.baseUrl?has_content>
            <a href="${client.baseUrl}/forgot-password">${msg("doForgotPassword")}</a>
          <#else>
            <a href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
          </#if>
        </#if>
      </div>
    </#if>

    <#-- Carries the chosen credential through an MFA step. Harmless when
         unset; omitting it breaks credential selection once OTP is on. -->
    <input type="hidden" name="credentialId" value="${(auth.selectedCredential!'')}">

    <button class="btn-gradient" type="submit" name="login">${msg("doLogIn")}</button>
  </form>
  </#if>

  <#if social?? && social.providers?? && social.providers?has_content>
    <div class="social">
      <div class="social-label">${msg("identity-provider-login-label")}</div>
      <div class="social-list">
        <#list social.providers as p>
          <a class="social-btn" href="${p.loginUrl}" id="social-${p.alias}">
            <@providerMark alias=p.alias/>
            <span>${p.displayName!p.alias}</span>
          </a>
        </#list>
      </div>
    </div>
  </#if>

</@k.page>
