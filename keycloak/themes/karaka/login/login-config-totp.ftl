<#--
  TOTP enrolment — the QR code screen.

  Nothing here is custom cryptography: Keycloak generates the shared secret, renders the QR as
  a base64 PNG, and validates the first code to prove the clocks agree before it saves the
  credential. This template only presents what Keycloak already produced.

  Reached by the CONFIGURE_TOTP required action — set per user by an admin, made realm-wide by
  marking it a default action, or triggered by the application with
  ?kc_action=CONFIGURE_TOTP so a "Set up 2FA" button needs no form of its own.

  The secret is shown in text as well as the QR because a phone cannot scan the screen it is
  already displaying — a laptop user scans, a phone user copies.
-->
<#import "karaka-layout.ftl" as k>

<@k.page pageTitle=msg("loginTotpTitle") heading=msg("loginTotpTitle")
         subtitle="Scan the code with your authenticator app, then enter the six digits it shows."
         showAlert=false
         footNote="Codes rotate every ${totp.policy.period} seconds. Keep the app; you will need it at each sign-in.">

  <#if messagesPerField.existsError('totp','userLabel')>
    <div class="alert alert-error" role="alert">
      ${kcSanitize(messagesPerField.getFirstError('totp','userLabel'))?no_esc}
    </div>
  </#if>

  <ol class="totp-steps">
    <li>
      Install an authenticator app
      <span class="totp-apps">
        <#list totp.supportedApplications as app>${msg(app)}<#sep> · </#sep></#list>
      </span>
    </li>
    <li>
      Scan this code
      <div class="totp-qr">
        <#-- Keycloak hands us a base64 PNG, so no external request and no QR library. -->
        <img src="data:image/png;base64,${totp.totpSecretQrCode}"
             alt="${msg("loginTotpScanBarcode")}" width="180" height="180">
      </div>
      <details class="totp-manual">
        <summary>Can't scan it?</summary>
        <p>Enter this key in the app by hand:</p>
        <code class="totp-secret">${totp.totpSecretEncoded}</code>
        <p class="totp-meta">
          Type ${totp.policy.type} &middot; Algorithm ${totp.policy.algorithm}
          &middot; Digits ${totp.policy.digits} &middot; Interval ${totp.policy.period}s
        </p>
      </details>
    </li>
    <li>Enter the code it shows</li>
  </ol>

  <form class="form" action="${url.loginAction}" method="post" novalidate>
    <div class="field-float">
      <#-- inputmode/autocomplete give phones a numeric pad and let them autofill an SMS-style
           code; one-time-code is the standard token for exactly this field. -->
      <input class="input-float" id="totp" name="totp" type="text" placeholder=" "
             autocomplete="one-time-code" inputmode="numeric" pattern="[0-9]*"
             maxlength="${totp.policy.digits}" autofocus
             aria-invalid="${messagesPerField.existsError('totp')?c}">
      <label for="totp">${msg("authenticatorCode")}</label>
    </div>

    <div class="field-float">
      <#-- Names the device, so someone with a phone and a tablet can tell them apart later
           and revoke just one. -->
      <input class="input-float" id="userLabel" name="userLabel" type="text" placeholder=" "
             aria-invalid="${messagesPerField.existsError('userLabel')?c}">
      <label for="userLabel">${msg("loginTotpDeviceName")}</label>
    </div>

    <input type="hidden" id="totpSecret" name="totpSecret" value="${totp.totpSecret}">
    <#if mode??><input type="hidden" id="mode" name="mode" value="${mode}"></#if>

    <button class="btn-gradient" type="submit">${msg("doSubmit")}</button>

    <#-- Cancel only for an app-initiated action. A required action raised by an admin must not
         be dismissable, or 2FA becomes optional in practice. -->
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
