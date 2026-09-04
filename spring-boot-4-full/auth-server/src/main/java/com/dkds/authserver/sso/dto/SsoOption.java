package com.dkds.authserver.sso.dto;

/// One IdP a user can sign in through, offered by SsoDiscoveryController's
/// org picker screen. registrationId is what /saml2/authenticate/{registrationId}
/// takes; orgName is the label shown to the user.
public record SsoOption(String registrationId, String orgName) {
}
