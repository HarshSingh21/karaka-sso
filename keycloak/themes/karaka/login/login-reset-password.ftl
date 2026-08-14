<#--
  "Forgot Password?" — collects a username or email so Keycloak can send a reset link.

  Keycloak sends a LINK, never a password. That is deliberate on its part and worth keeping:
  email is plaintext at rest and lives in the inbox indefinitely.

  This screen needs SMTP on the realm. Without smtpServer configured, submitting throws
  FreeMarkerEmailTemplateProvider.sendPasswordReset -> EmailException, and the user lands on
  an error with no idea why. See scripts/set-smtp.sh.
-->
<#import "karaka-layout.ftl" as k>

<@k.page pageTitle=msg("emailForgotTitle") heading=msg("emailForgotTitle")
         subtitle=msg("emailInstruction")
         footNote="Links expire shortly. Still stuck? Contact your workspace admin.">

  <form class="form" action="${url.loginAction}" method="post" novalidate>
    <div class="field-float">
      <input class="input-float" id="username" name="username" type="text"
             placeholder=" " autofocus autocomplete="username"
             <#-- Carries over whatever they already typed on the sign-in page, so a
                  mistyped password does not cost them the username too. -->
             value="${(auth.attemptedUsername!'')}"
             aria-invalid="${messagesPerField.existsError('username')?c}">
      <label for="username">
        <#if !realm.loginWithEmailAllowed>
          ${msg("username")}
        <#elseif !realm.registrationEmailAsUsername>
          ${msg("usernameOrEmail")}
        <#else>
          ${msg("email")}
        </#if>
      </label>
      <#if messagesPerField.existsError('username')>
        <div class="field-error" aria-live="polite">
          ${kcSanitize(messagesPerField.get('username'))?no_esc}
        </div>
      </#if>
    </div>

    <button class="btn-gradient" type="submit">${msg("doSubmit")}</button>

    <div class="form-aside">
      <span></span>
      <#-- msg("backToLogin") ships with markup (a « entity), so it must not be escaped. -->
      <a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a>
    </div>
  </form>

</@k.page>
