<#--
  Karaka sign-in — standalone template.

  Deliberately does NOT `<#import "template.ftl" as layout>`. The base theme's
  registrationLayout macro brings its own page chrome, and the split-hero needs
  control of <body> down to the seam, so inheriting it would mean overriding
  most of it back out again. parent=base in theme.properties still gives us the
  FreeMarker beans (realm/url/login/message/messagesPerField) and the i18n
  bundle, which is all this file actually needs.

  Keycloak configures FreeMarker with HTML output format, so ${...} is
  auto-escaped. Server messages are pre-sanitised HTML and therefore need
  kcSanitize(...)?no_esc — that pairing is intentional, not a leftover.
-->
<!doctype html>
<#-- Parenthesised so an undefined `locale` bean falls back too: the realm has
     internationalization off, which leaves the bean absent rather than empty. -->
<html lang="${(locale.currentLanguageTag)!'en'}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <#-- "Karaka" is the suite brand and never varies by tenant, so it is literal
       here. The tenant's own name is the .org-tag below. -->
  <title>Sign in &mdash; Karaka</title>
  <link rel="icon" href="${url.resourcesPath}/img/favicon.svg" type="image/svg+xml">
  <link rel="stylesheet" href="${url.resourcesPath}/css/karaka.css">
</head>
<body>

<#-- The six ambient hero icons: literal to the suite's domain (document,
     chart, grid, shield, spark, clock), not invented login imagery. -->
<#assign floatIcons = [
  {"d": "M14 4h20l10 10v26H14z M34 4v10h10",                  "x": "10%", "y": "16%", "size": 44, "delay": "0s"},
  {"d": "M8 40h32 M14 40V26 M24 40V16 M34 40V22",             "x": "70%", "y": "12%", "size": 50, "delay": "1.4s"},
  {"d": "M8 8h12v12H8z M28 8h12v12H28z M8 28h12v12H8z M28 28h12v12H28z", "x": "20%", "y": "60%", "size": 52, "delay": "2.6s"},
  {"d": "M24 6l16 6v12c0 11-7 18-16 22-9-4-16-11-16-22V12z",  "x": "78%", "y": "50%", "size": 42, "delay": "0.7s"},
  {"d": "M24 4l4 16 16 4-16 4-4 16-4-16-16-4 16-4z",          "x": "46%", "y": "28%", "size": 34, "delay": "3.3s"},
  {"d": "M24 6a18 18 0 100 36 18 18 0 000-36z M24 14v10l8 5", "x": "58%", "y": "72%", "size": 40, "delay": "1.9s"}
]>

<div class="shell">

  <div class="hero">
    <div class="hero-blob b1"></div>
    <div class="hero-blob b2"></div>
    <#list floatIcons as ic>
      <div class="float-ic" style="left:${ic.x};top:${ic.y};animation-delay:${ic.delay}" aria-hidden="true">
        <svg width="${ic.size?c}" height="${ic.size?c}" viewBox="0 0 48 48" fill="none" stroke="currentColor"
             stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="${ic.d}"/></svg>
      </div>
    </#list>
    <div class="hero-copy">
      <h1 class="display">ONE LOGIN.<br>EVERY TOOL,<br><span class="hl">ONE KARAKA.</span></h1>
      <p>Sign in once to reach ORBIT, AURA, and PULSE &mdash; your directory, your assistant,
         and your attendance, all under Karaka.</p>
    </div>
  </div>

  <div class="form-side">
    <div class="form-wrap form-wrap-signin">

      <div class="brand-row">
        <div class="brand-lockup">
          <div class="brand-mark">
            <#-- Suite mark: three nodes converging on one point, echoing the
                 name itself (karaka = the agent that brings the action about). -->
            <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true">
              <path d="M14 32 L24 14 L34 32" stroke="#fff" stroke-width="2.6" fill="none"
                    stroke-linecap="round" stroke-linejoin="round"/>
              <circle cx="14" cy="32" r="5" fill="#fff"/>
              <circle cx="34" cy="32" r="5" fill="#fff"/>
              <circle cx="24" cy="14" r="5" fill="#fff"/>
            </svg>
          </div>
          <div class="brand-name">KARAKA</div>
        </div>
        <#-- Tenant context. One realm per customer, so the realm's display name
             is the org tag — it is never a second brand competing with Karaka. -->
        <div class="org-tag">${realm.displayName!realm.name}</div>
      </div>

      <#assign hasError = (message?? && message.type == 'error')>
      <div class="glass<#if hasError> shake</#if>">
        <h2>${msg("doLogIn")}</h2>
        <p class="sub">Use your work email to continue.</p>

        <#if message?? && message.summary?has_content>
          <div class="alert alert-${message.type}" role="alert">
            ${kcSanitize(message.summary)?no_esc}
          </div>
        </#if>

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
              <#if realm.resetPasswordAllowed>
                <a href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
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
                  ${p.displayName!p.alias}
                </a>
              </#list>
            </div>
          </div>
        </#if>
      </div>

      <div class="foot-note">Trouble signing in? Contact your workspace admin.</div>
    </div>
  </div>

</div>
</body>
</html>
