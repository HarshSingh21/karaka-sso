<#--
  Expired authentication page. Reachable in normal use: reset-password links expire, and a
  login form left open past ssoSessionIdleTimeout lands here too.

  Unlike error.ftl this page CAN recover, and that is the whole reason it exists as a separate
  template. Keycloak supplies two live URLs here:

    url.loginRestartFlowUrl   start the flow again from the beginning
    url.loginAction           continue where the user left off, when possible

  Both are real, session-aware URLs — not the relative continuation URL that made error.ftl's
  button do nothing. Offer restart first: it is the option that always works.
-->
<#import "karaka-layout.ftl" as k>

<@k.page pageTitle=msg("pageExpiredTitle") heading=msg("pageExpiredTitle")
         subtitle="This page timed out. Start again and you will be signed in."
         showAlert=false
         footNote="Links and forms expire for your security.">

  <div class="social-list">
    <a class="social-btn" id="loginRestartLink" href="${url.loginRestartFlowUrl}">
      ${kcSanitize(msg("doClickHere"))?no_esc} &mdash; ${kcSanitize(msg("pageExpiredMsg1"))?no_esc}
    </a>
    <a class="social-btn" id="loginContinueLink" href="${url.loginAction}">
      ${kcSanitize(msg("doClickHere"))?no_esc} &mdash; ${kcSanitize(msg("pageExpiredMsg2"))?no_esc}
    </a>
  </div>

</@k.page>
