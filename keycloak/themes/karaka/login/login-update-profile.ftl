<#--
  "Update Account Information" — the UPDATE_PROFILE required action.

  Themed alongside idp-review-user-profile.ftl because BOTH can render this screen and which
  one Keycloak picks is not obvious from the outside. The IdP review authenticator uses
  idp-review-user-profile.ftl; the UPDATE_PROFILE required action uses this file. A brokered
  login that arrives without a required attribute can go through either path depending on
  realm and flow configuration, and an unthemed fallback showed up in testing precisely
  because only one of the two was overridden.

  Reachable without any identity provider too: an admin can add UPDATE_PROFILE to a user, and
  Keycloak raises it whenever a required user-profile attribute is missing.

  Fields come from the realm's user profile via `profile.attributes` rather than being
  hard-coded, so requiring a new attribute in Keycloak changes this form with no edit here.
-->
<#import "karaka-layout.ftl" as k>

<@k.page pageTitle=msg("loginProfileTitle") heading=msg("loginProfileTitle")
         subtitle="A few details are missing from your account. Confirm them to continue."
         <#-- showAlert=false: field-level errors are rendered inline against each input
              below, so the shared banner would print "Please specify this field." a second
              time and read as two separate problems. -->
         showAlert=false
         footNote="Fields marked * are required by your Karaka workspace.">

  <form class="form" action="${url.loginAction}" method="post" novalidate>
    <#list profile.attributes as attribute>
      <div class="field-float">
        <input class="input-float"
               type="<#if attribute.name == 'email'>email<#else>text</#if>"
               id="${attribute.name}" name="${attribute.name}"
               placeholder=" "
               value="${(attribute.value!'')}"
               <#if attribute.required>required</#if>
               <#if attribute.readOnly>disabled</#if>
               <#if attribute.autocomplete??>autocomplete="${attribute.autocomplete}"</#if>
               aria-invalid="${messagesPerField.existsError('${attribute.name}')?c}">
        <label for="${attribute.name}">
          ${advancedMsg(attribute.displayName!attribute.name)}<#if attribute.required> *</#if>
        </label>
        <#if messagesPerField.existsError('${attribute.name}')>
          <div class="field-error" aria-live="polite">
            ${kcSanitize(messagesPerField.get('${attribute.name}'))?no_esc}
          </div>
        </#if>
      </div>
    </#list>

    <button class="btn-gradient" type="submit">${msg("doSubmit")}</button>

    <#-- Offered only for an app-initiated action. A required action raised because the account
         is incomplete must not be dismissable, or the user proceeds without the attribute the
         realm says it needs — which is how a user ends up with a NULL email. -->
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
