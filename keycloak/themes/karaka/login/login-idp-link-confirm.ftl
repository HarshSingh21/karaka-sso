<#--
  Account linking. Shown when a brokered identity's email already belongs to a local Karaka
  user: the person may be the same human arriving by a second route, or someone claiming an
  address that is not theirs.

  Reachable as soon as a second identity provider exists — one person signing in with Google
  and later with Twitter using the same address lands here.

  Only two ways forward, and neither is "trust it": link after proving ownership of the
  existing account, or review the profile and use a different address. The confirmation is
  the security control, so it must be legible rather than an unstyled fallback page.
-->
<#import "karaka-layout.ftl" as k>

<@k.page pageTitle=msg("confirmLinkIdpTitle") heading=msg("confirmLinkIdpTitle")
         subtitle=msg("federatedIdentityConfirmLinkMessage", idpAlias, idpDisplayName!idpAlias)
         showAlert=false
         footNote="If you do not recognise this account, choose Review profile instead of linking.">

  <form class="form" action="${url.loginAction}" method="post">
    <button class="btn-gradient" type="submit" name="submitAction" value="linkAccount">
      ${msg("confirmLinkIdpContinue", idpDisplayName!idpAlias)}
    </button>
    <div class="social-list">
      <button class="social-btn" type="submit" name="submitAction" value="updateProfile"
              style="cursor:pointer">
        ${msg("confirmLinkIdpReviewProfile")}
      </button>
    </div>
  </form>

</@k.page>
