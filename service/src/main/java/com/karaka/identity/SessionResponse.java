package com.karaka.identity;

import java.util.List;

/**
 * Who the caller is, as the UI needs it.
 *
 * <p>This is what makes the product picker possible without the browser ever
 * decoding a token. The old prototype hard-coded its three tiles and marked two
 * "SOON"; {@code entitlements} replaces that with the truth for this user.
 *
 * <p>Only display data and entitlements — never the access token, the refresh
 * token, or raw claims. Putting a token here would undo the reason for holding it
 * server-side in the first place.
 *
 * @param entitlements product codes the user may enter, e.g. {@code ["ORBIT"]},
 *     derived from {@code PRODUCT_*} realm roles
 * @param roles every granted role, so a UI can hide an action it would be
 *     refused anyway. The server still enforces it — this is cosmetic.
 */
public record SessionResponse(
    String username,
    String displayName,
    String email,
    String initials,
    String tenant,
    List<String> roles,
    List<String> entitlements) {}
