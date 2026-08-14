<#--
  Neutral outcome page. Keycloak reuses it for several unrelated results:
    - "You should receive an email shortly with further instructions."  (forgot password)
    - "Your account has been updated."                                  (password changed)
    - email-verification and required-action confirmations

  Because one template serves all of them, nothing here may assume a specific outcome.
  An earlier version titled the page "Forgot Your Password?" and footed it with "Not seeing
  the email?", which read as a contradiction on the password-changed case — the page said
  the account was updated under a heading asking about a forgotten password.

  So: a neutral heading, the server's own message as the body, and always a way onward.

  showAlert=false on the layout — the message is this page's whole content and is rendered
  below, so the shared alert box would print it a second time.
-->
<#import "karaka-layout.ftl" as k>

<#-- Tab title and heading share one fallback so they can never disagree — the tab said
     "Sign In" while the card said "Notice" when these were derived separately. -->
<#assign infoHeading = (messageHeader!"Notice")>

<@k.page pageTitle=infoHeading
         heading=infoHeading
         showAlert=false
         footNote="Need help? Contact your workspace admin.">

  <#if message?? && message.summary?has_content>
    <p class="sub">
      ${kcSanitize(message.summary)?no_esc}<#if requiredActions??><#list requiredActions>: <#items as a>${kcSanitize(msg("requiredAction.${a}"))?no_esc}<#sep>, </#items></#list></#if>
    </p>
  </#if>

  <#-- Always offer exactly one way onward, in Keycloak's order of preference, falling back
       to the login page. skipLink means the caller explicitly wants none — usually because
       the browser is about to be redirected anyway. Without this fallback the page is a dead
       end and the user has to retype a URL, which is where the flow felt broken. -->
  <#if skipLink??>
  <#elseif pageRedirectUri?has_content>
    <a class="social-btn" href="${pageRedirectUri}">${kcSanitize(msg("backToApplication"))?no_esc}</a>
  <#elseif actionUri?has_content>
    <a class="social-btn" href="${actionUri}">${kcSanitize(msg("proceedWithAction"))?no_esc}</a>
  <#elseif client?? && client.baseUrl?has_content>
    <a class="social-btn" href="${client.baseUrl}">${kcSanitize(msg("backToApplication"))?no_esc}</a>
  <#else>
    <a class="social-btn" href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a>
  </#if>

</@k.page>
