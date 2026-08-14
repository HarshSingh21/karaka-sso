<#--
  Second factor at sign-in: enter the code from the authenticator app.

  Reached automatically once a user has a TOTP credential, because the browser flow already
  contains "Browser - Conditional 2FA -> OTP Form". Conditional means users without a
  credential are unaffected, so enabling 2FA for one person does not force it on everyone.

  The credential selector appears only when someone has enrolled more than one device —
  otherwise it is a radio group with a single option, which is noise.
-->
<#import "karaka-layout.ftl" as k>

<@k.page pageTitle=msg("doLogIn") heading=msg("loginOtpOneTime")
         subtitle="Open your authenticator app and enter the current code."
         showAlert=false
         footNote="Lost your device? Your workspace admin can remove the second factor.">

  <#if messagesPerField.existsError('totp')>
    <div class="alert alert-error" role="alert">
      ${kcSanitize(messagesPerField.getFirstError('totp'))?no_esc}
    </div>
  </#if>

  <form class="form" action="${url.loginAction}" method="post" novalidate>

    <#if otpLogin?? && otpLogin.userOtpCredentials?size gt 1>
      <div class="otp-choice">
        <div class="social-label">${msg("loginChooseAuthenticator")}</div>
        <#list otpLogin.userOtpCredentials as credential>
          <label class="remember">
            <input type="radio" name="selectedCredentialId" value="${credential.id}"
                   <#if credential.id == otpLogin.selectedCredentialId>checked</#if>>
            ${credential.userLabel}
          </label>
        </#list>
      </div>
    </#if>

    <div class="field-float">
      <#-- autocomplete="one-time-code" lets iOS and Android offer the code from the app or
           clipboard, which is the difference between two taps and retyping six digits. -->
      <input class="input-float" id="otp" name="otp" type="text" placeholder=" "
             autocomplete="one-time-code" inputmode="numeric" pattern="[0-9]*"
             autofocus aria-invalid="${messagesPerField.existsError('totp')?c}">
      <label for="otp">${msg("loginOtpOneTime")}</label>
    </div>

    <button class="btn-gradient" type="submit" name="login">${msg("doLogIn")}</button>
  </form>

</@k.page>
