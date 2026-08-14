<#--
  Review profile, shown on first brokered login when the provider did not supply everything
  this realm requires.

  Unavoidable for Twitter: its Keycloak provider is OAuth 1.0a and returns NO email address
  (X gates email behind elevated "Request email from users" permission), while this realm's
  user profile marks email required for role `user`. So every new Twitter user sees this page.
  GitHub reaches it too, whenever the account has no public email.

  Fields are driven by the realm's user profile via the `profile.attributes` bean rather than
  hard-coded, so enabling or requiring an attribute in Keycloak changes this form with no edit
  here — and nothing this realm requires can silently go uncollected.
-->
<#import "karaka-layout.ftl" as k>

<@k.page pageTitle=msg("loginProfileTitle") heading=msg("loginProfileTitle")
         subtitle="Your provider did not share everything we need. Confirm these details to finish."
         <#-- showAlert=false: field-level errors are rendered inline against each input
              below, so the shared banner would print "Please specify this field." a second
              time and read as two separate problems. -->
         showAlert=false
         footNote="We only ask for what your Karaka account requires.">

  <form class="form" action="${url.loginAction}" method="post" novalidate>
    <#list profile.attributes as attribute>
      <div class="field-float">
        <input class="input-float" type="<#if attribute.name == 'email'>email<#else>text</#if>"
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
  </form>

</@k.page>
