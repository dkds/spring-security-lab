# Agent Instructions — spring-security-lab

Inherits global rules from `~/devbox/AGENTS.md`. This file adds project-specific
context and a record of work completed so agents can resume without re-exploring.

---

## Project Overview

Multi-tenant Spring Security Authorization Server POC.

| Module | Port | DB |
|--------|------|----|
| `auth-server` | 9000 | `authdb` (MySQL on `mysql-auth:13306`) |
| `resource-server` | 8082 | `resourcedb` (MySQL on `mysql-resource:13307`) |
| `ui` | 5173 (dev) | — |

**Stack:** Java 25, Spring Boot 4.1.0, Spring Security 7.1.0, Maven, Vite/React, pnpm

**Profiles:**
- Default (`application.yaml`): `localhost:3307` / `localhost:3308` (local MySQL)
- `devpod` (`application-devpod.yaml`): `mysql-auth:13306` / `mysql-resource:13307` (devcontainer)
- `test` (`application-test.yaml`): H2 in-memory, `create-drop`

---

## Implementation Status

### ✅ Phase 0 — Scaffold
- Both `auth-server` and `resource-server` bootstrapped as standalone Spring Boot apps
- `ui/` Vite/React app with react-oidc-context OAuth2 library
- `application-devpod.yaml` profiles added to both servers

### ✅ Phase 1 — Domain Entities & Seed Data

**Package: `user/`**
- `AppUser` — entity: `id`, `username`, `passwordHash`, `enabled`, `lastLoginAt`, `failedAttempts`, `lockedUntil`; includes `isLocked()` / `clearLock()` helpers
- `UserRepository` — `findByUsername()`

**Package: `organization/`**
- `Organization` — entity: `id`, `code`, `name`, `active`
- `Membership` — composite key `(user_id, org_id)` + `active`; uses `@IdClass(MembershipId)`
- `MembershipId` — composite key class (Serializable)
- `OrgSecurityPolicy` — 1:1 with Organization; `orgId` as PK, `mfaMode` (enum), `ipRestrictionEnabled`
- `OrgIpRange` — CIDR blocks per org
- `MfaMode` — enum: `NEVER`, `DAILY`, `EVERY_30_DAYS`, `EVERY_LOGIN`
- Repositories: `OrganizationRepository`, `MembershipRepository`, `OrgSecurityPolicyRepository`, `OrgIpRangeRepository`

**Package: `authorization/`**
- `UserVerification` — tracks MFA method + verified timestamp per user
- `UserVerificationRepository` — `findByUserIdAndMethod()`

**Package: `common/`**
- `DataInitializer` — seeds 3 users, 6 orgs (various MFA modes, IP restriction, SSO-ready), memberships, verifications on startup (guarded by `@ConditionalOnProperty`)

**Package: `security/`**
- `SecurityConfig` — provides `PasswordEncoder` (`BCryptPasswordEncoder`) bean

### ✅ Phase 2 — Security Filter Chains + Form Login + OAuth2 PKCE

**Key design rules followed:**
- Exactly **3 filter chains** in order:
  1. `@Order(1)` — `authServer.getEndpointsMatcher()` (OAuth2 + OIDC + discovery)
  2. `@Order(2)` — `/api/**` STATELESS JWT resource server
  3. `@Order(3)` — fallback: form login + OTT + SAML2
- `@EnableMultiFactorAuthentication(authorities = {})` on `SecurityChains`
- Entry point: `LoginUrlAuthenticationEntryPoint` on both Chain 1 and Chain 3
- Chain 1: `.exceptionHandling()` wires `LoginUrlAuthenticationEntryPoint` so unauthenticated `/oauth2/authorize` redirects to `/login`
- Terminal rejections via **UserDetails flags** (`.disabled()`, `.accountLocked()`, `.accountExpired()`, `.credentialsExpired()`) — NOT custom exceptions
- `FormLoginConfigurer extends AbstractHttpConfigurer` applied via `http.with(...)`
- `IdentityChangeAwareSessionStrategy` wired into Chain 3 via `FormLoginConfigurer`; shares same `HttpSessionRequestCache(matcher=/oauth2/authorize)` instance
- CSRF: ignored on `endpointsMatcher` (Chain 1), disabled on Chain 2, enabled on Chain 3
- CORS: `CorsConfigurationSource` bean in `CorsConfig`; covers `/oauth2/token`, `/oauth2/jwks`, `/oauth2/introspect`, `/oauth2/revoke`, `/userinfo`, `/connect/logout`, `/.well-known/**`

**Package: `user/`**
- `AppUserDetailsService` — loads `AppUser`, checks enabled/locked/active-membership flags
- `UserService` — `findActiveMemberships()` business logic

**Package: `login/`**
- `FormLoginConfigurer` — `AbstractHttpConfigurer`; sets up form login, shared request cache, session strategy
- `LoginController` — `GET /login` → `login.html`; `GET /ott/input` → `ott-input.html`
- `LoginAttempt` entity + `LoginAttemptRepository` (for Phase 4 rate limiting)

**Package: `token/`**
- `RegisteredClientConfig` — registers `spa-client` with PKCE required; also holds `AuthorizationServerSettings` bean (issuer from `app.oauth2.issuer`)
- `AccessTokenCustomizer` — adds `ROLE_` authorities to token (namespace-safe)
- `JwkConfig` — RSA key pair + `JWKSource` bean for token signing

**Package: `security/`**
- `SecurityChains` — all 3 `@Bean SecurityFilterChain` definitions
- `SecurityConstants` — `LOGIN_PAGE = "/login"`, role/permission constants
- `IdentityChangeAwareSessionStrategy` — clears request cache on principal change; delegates to `ChangeSessionIdAuthenticationStrategy`

**Package: `common/`**
- `CorsConfig` — `CorsConfigurationSource` bean (allowed origins from `app.cors.allowed-origins`)

**UI (`ui/`):**
- `react-oidc-context` library (replaces all custom OAuth2 code)
- `oidcConfig.ts` — OIDC config: `client_id=spa-client`, `scope=openid profile email`, `loadUserInfo=true`, `WebStorageStateStore` (localStorage)
- `useAuthHook.ts` — lean hook wrapping `useAuth()`
- `RequireAuth.tsx` — calls `auth.signinRedirect()` directly when unauthenticated
- `CallbackPage.tsx` — uses `useNavigate()` after token exchange
- `LoginPage.tsx` — immediately redirects to auth-server (no local login form)
- Deleted: `pkce.ts`, `storage.ts`, `authService.ts`, `AuthContext.tsx`, `config.ts`

**Client ID:** `spa-client` (consistent everywhere — `RegisteredClientConfig`, `oidcConfig.ts`, `.env.local`, `.env.example`)

### ✅ Phase 3 — MFA with One-Time Tokens (OTT)

**Package: `onetimetoken/`**
- `OneTimeToken` — JPA entity (`one_time_token` table): `tokenValue` (PK, 6-char), `username`, `expiresAt`
- `OneTimeTokenRepository` — `findByTokenValueAndExpiresAtAfter()`, `deleteExpiredTokens()`
- `NumericOneTimeTokenService` — implements `OneTimeTokenService`; generates cryptographically random 6-digit code via `SecureRandom`; 5-min TTL; single-use (deleted on consume); constant-time comparison via `MessageDigest.isEqual()`
- `EmailOttDeliveryHandler` — sends code via `JavaMailSender` (Mailpit); redirects to `/ott/input`; `OTT_INPUT_URL = "/ott/input"` (public constant)
- `OneTimeTokenConfigurer` — `AbstractHttpConfigurer`; configures `http.oneTimeTokenLogin(...)` with `showDefaultSubmitPage(false)`, custom token service and delivery handler
- `OttConfig` — `@Configuration`; wires `NumericOneTimeTokenService`, `EmailOttDeliveryHandler`, `OneTimeTokenConfigurer` beans; provides `Clock` bean; reads `app.mail.from` property

**Templates:**
- `templates/ott-input.html` — custom 6-digit code entry screen (Thymeleaf); auto-advance between digit inputs, paste support, CSRF token included

**Infrastructure:**
- `compose.yaml` — Mailpit added: SMTP port 1025, Web UI port 8025
- `application.yaml` — mail: `host=localhost`, `port=1025`, SMTP auth off
- `application-devpod.yaml` — mail: `host=mailpit`, `port=1025`

**Key design rules followed:**
- No `otp_challenge` table — `one_time_token` IS the OTT storage
- 6-digit numeric codes only (not magic links)
- 5-minute TTL enforced on every `generate()`
- Single-use: token deleted on `consume()`
- `showDefaultSubmitPage(false)` with custom `/ott/input` endpoint in `LoginController`
- Schema managed by Hibernate `ddl-auto` (no manual SQL init scripts)

---

## What's Next

| Phase | Topic | Status |
|-------|-------|--------|
| 4 | Per-org MFA policy enforcement, OTT rate limiting, login recording | 🔜 Next |
| 5 | IP restriction (`OrgIpAuthorizationManager`) | ⏳ |
| 6 | Resource server + `common-security` module + JWT validation | ⏳ |
| 7 | SAML2 SP-initiated SSO via Keycloak | ⏳ |
| 8 | IdP-initiated flow + identity change strategy | ⏳ |
| 9 | Captcha filter | ⏳ |
| 10 | IdP MFA mapping (`AuthnContextClassRef` → `FACTOR_IDP_MFA`) | ⏳ |
| 11 | ArchUnit enforcement | ⏳ |

---

## Test Status

```
auth-server:
  Tests run: 8, Failures: 0, Errors: 0
  - AuthServerApplicationTests (1) — contextLoads
  - Phase 2: Filter Chains & Terminal Rejections (3)
  - Phase 3: MFA with One-Time Tokens (4)

resource-server:
  Tests run: 1, Failures: 0, Errors: 0
  - ResourceServerApplicationTests (1) — contextLoads
```

Run with: `cd auth-server && mvn -q test`

---

## Known Gaps / Watch Points

1. **OTT end-to-end spike** — the critical Phase 3 test (saved `/oauth2/authorize` request replayed after OTT) requires running MySQL + Mailpit stack. Unit tests verify OTT mechanics only.
2. **`IdentityChangeAwareSessionStrategy`** — fully wired but only exercised manually (Phase 7 SSO will stress-test it).
3. **Login recording** (`last_login_at`, `failedAttempts`) — entity fields exist but not yet updated on auth events (Phase 4).
4. **`DataInitializer`** — runs on every startup; guarded by `app.data.seed=true` property. Do not enable in production.
5. **`ddl-auto: update`** — fine for dev/POC; must change to `validate` before any production use.
