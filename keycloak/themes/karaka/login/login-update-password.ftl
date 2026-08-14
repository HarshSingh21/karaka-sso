<#--
  Forced password change (required action UPDATE_PASSWORD).

  Reached three ways: forceExpiredPasswordChange in the realm password policy, an admin
  setting a temporary password, or the app sending the user here with
  ?kc_action=UPDATE_PASSWORD.

  There is NO old-password field, and that is correct: the user authenticated with the old
  password seconds ago, so asking again proves nothing. The three-field form with a current
  password is the Account Console (/realms/karaka/account), where there is no fresh
  authentication to lean on.

  With forceExpiredPasswordChange(4) on the realm, this becomes one of the most-visited
  screens in the product — which is exactly why it cannot be an unstyled fallback page.
-->
<#import "karaka-layout.ftl" as k>

<#assign pwError = messagesPerField.existsError('password','password-confirm')>

<@k.page pageTitle=msg("updatePasswordTitle") heading=msg("updatePasswordTitle")
         subtitle="Choose a new password to continue."
         footNote="Your new password must differ from your recent ones.">

  <form class="form" action="${url.loginAction}" method="post" novalidate>
    <#-- Present so password managers can associate the entry with the right account.
         Hidden and non-editable; Keycloak ignores it. -->
    <input type="text" id="username" name="username" value="${username!''}"
           autocomplete="username" readonly="readonly" hidden="hidden">

    <div class="field-float">
      <input class="input-float" id="password-new" name="password-new" type="password"
             placeholder=" " autofocus autocomplete="new-password"
             aria-invalid="${pwError?c}">
      <label for="password-new">${msg("passwordNew")}</label>
      <#if messagesPerField.existsError('password')>
        <div class="field-error" aria-live="polite">
          ${kcSanitize(messagesPerField.get('password'))?no_esc}
        </div>
      </#if>
    </div>

    <div class="field-float">
      <input class="input-float" id="password-confirm" name="password-confirm" type="password"
             placeholder=" " autocomplete="new-password"
             aria-invalid="${pwError?c}">
      <label for="password-confirm">${msg("passwordConfirm")}</label>
      <#if messagesPerField.existsError('password-confirm')>
        <div class="field-error" aria-live="polite">
          ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
        </div>
      </#if>
    </div>

    <#-- Keycloak offers this so a password change can also end other sessions — the thing
         you actually want if the old password may be known to someone else. Checked by
         default, matching Keycloak's own behaviour. -->
    <#if isAppInitiatedAction??>
      <div class="form-aside">
        <label class="remember">
          <input type="checkbox" id="logout-sessions" name="logout-sessions" value="on" checked>
          ${msg("logoutOtherSessions")}
        </label>
        <span></span>
      </div>
    <#else>
      <input type="hidden" name="logout-sessions" value="on">
    </#if>

    <button class="btn-gradient" type="submit">${msg("doSubmit")}</button>

    <#-- Only offered when the app initiated the action, never when the password has
         expired: letting someone dismiss a required action would defeat the policy. -->
    <#if isAppInitiatedAction??>
      <div class="form-aside">
        <span></span>
        <button type="submit" name="cancel-aia" value="true" class="link-button">
          ${msg("doCancel")}
        </button>
      </div>
    </#if>
  </form>

</@k.page>
