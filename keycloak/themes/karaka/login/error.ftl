<#--
  Terminal error page. This is what a user sees when the reset flow fails server-side —
  including the SMTP failure when no mail server is configured — so leaving it on the
  unstyled base fallback puts a broken-looking page at the worst possible moment.

  showAlert=false: the message IS the page content and is rendered below, so the shared
  alert box would duplicate it.
-->
<#import "karaka-layout.ftl" as k>

<@k.page pageTitle=msg("errorTitle") heading=msg("errorTitle")
         showAlert=false
         footNote="Open Karaka again to start over. If it keeps happening, contact your workspace admin.">

  <#if message?? && message.summary?has_content>
    <div class="alert alert-error" role="alert">
      ${kcSanitize(message.summary)?no_esc}
    </div>
  </#if>

  <#--
    client.baseUrl is the ONLY correct target here, and it requires baseUrl to be set on the
    client in the realm — which is why the realm template now declares rootUrl/baseUrl.

    Do NOT fall back to url.loginUrl. On this page it resolves to
      /realms/karaka/login-actions/authenticate?client_id=karaka-web
    which is a CONTINUATION url: it needs the authentication session that has just been
    reported missing, and it is relative, so it cannot even be followed from an email client.
    An earlier version linked there and the button did nothing — worse than no button, because
    the user retries it instead of starting over.

    With no client context at all there is genuinely nothing safe to link to, so the footnote
    below carries the instruction instead of rendering a link that cannot work.
  -->
  <#if client?? && client.baseUrl?has_content>
    <a class="social-btn" href="${client.baseUrl}">${kcSanitize(msg("backToApplication"))?no_esc}</a>
  </#if>

</@k.page>
