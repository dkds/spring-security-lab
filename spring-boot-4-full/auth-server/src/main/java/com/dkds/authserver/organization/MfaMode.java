package com.dkds.authserver.organization;

/// MFA requirement modes for an organization.
///
/// - NEVER: No MFA required
/// - DAILY: MFA required daily (24-hour interval)
/// - EVERY_30_DAYS: MFA required every 30 days
/// - EVERY_LOGIN: MFA required on every login
public enum MfaMode {
    NEVER,
    DAILY,
    EVERY_30_DAYS,
    EVERY_LOGIN
}
