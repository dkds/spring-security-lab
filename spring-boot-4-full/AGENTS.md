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

### ✅ Phase 4 — Per-Organization MFA Policy Enforcement

**Key finding that motivated this phase:** `@EnableMultiFactorAuthentication(authorities = {})` with an *empty* authorities array never publishes a `DefaultAuthorizationManagerFactory` bean at all — verified against Spring Security 7.1.0 source (`MultiFactorAuthenticationSelector` only imports `AuthorizationManagerFactoryConfiguration` when `authorities.length > 0`). With it empty, only the MFA filter machinery (OTT endpoints, missing-factor entry-point routing) gets wired; no factor requirement is ever composed into `anyRequest().authenticated()`. Confirmed live: `AuthorizationFilter` was falling back to a plain `AuthenticatedAuthorizationManager` that only checks "is authenticated," so any user with just `FACTOR_PASSWORD` sailed through regardless of org MFA policy.

**Package: `authorization/`**
- `OrgPolicyRequiredAuthoritiesRepository implements RequiredAuthoritiesRepository` (a real Spring Security 7.1 interface, one method: `findRequiredAuthorities(String username)`) — strictest-wins interval across the user's active memberships (`EVERY_LOGIN`=0, `DAILY`=1d, `EVERY_30_DAYS`=30d, `NEVER`=absent); requires `FACTOR_OTT` only when `user_verification.verified_at` (method `"EMAIL"`) is older than that interval; fails closed (returns `[FACTOR_OTT]`) on any exception, including a membership pointing at an org with no `OrgSecurityPolicy` row
- **Unknown username (notably `"anonymousUser"`) returns `[]`, not fail-closed.** `DefaultAuthorizationManagerFactory.withAdditionalAuthorization()` composes `AuthorizationManagers.allOf(deniedDecision, additionalAuthorization, baseManager)` — the additional manager runs *before* the base `authenticated()` check, so `findRequiredAuthorities` is invoked on every request including anonymous ones. Treating "no such user" as a fail-closed exception meant a `NoSuchElementException` (and a full stack trace at `WARN`) on every single unauthenticated request. An unknown username now short-circuits to `List.of()` — the base `authenticated()` check in the same composed manager is what denies those, same end result, no noise
- `AuthorizationPolicyConfig` — publishes the `AuthorizationManagerFactory<RequestAuthorizationContext>` bean (`DefaultAuthorizationManagerFactory` + `setAdditionalAuthorization(new RequiredAuthoritiesAuthorizationManager<>(orgPolicyRepository))`) that `AuthorizeHttpRequestsConfigurer` auto-discovers by generic type — applies to Chains 1 and 3 automatically, no per-chain wiring needed
- Deliberately depends only on `organization` + framework types, never on `com.dkds.authserver.security` (DESIGN.md: nothing may depend on `security`)
- `UserVerification.METHOD_EMAIL` constant added so the seed data (`DataInitializer`) and the policy lookup can't drift apart

**Package: `common/event/`**
- `LoginRecordingListener` — single `@EventListener` on `AuthenticationSuccessEvent`: writes `last_login_at` on every success; writes `user_verification.verified_at` only when the successful authentication's authorities include `FACTOR_OTT`; deletes any outstanding (unconsumed) OTT code for the user either way. One point, not per-mechanism, per DESIGN.md.

**Package: `onetimetoken/`**
- `OttAuthenticationFailureHandler` — per-code attempt cap of 5. `OneTimeTokenService` has no HTTP session access, so this lives at the filter layer: `EmailOttDeliveryHandler` stamps the pending username in session and resets the counter when a code is issued; this handler increments it on each failed `/login/ott` POST and, at the cap, deletes the user's outstanding code (forcing a fresh `/ott/generate`) and clears session state
- `NumericOneTimeTokenService.generate()` now deletes any existing outstanding code for the same username first — at most one live code per user, so a fresh request always supersedes a prior one
- `OneTimeTokenRepository.deleteByUsername(String)` added

**Package: `security/`**
- `SecurityChains` — Chain 3's `authorizeHttpRequests` now `permitAll()`s `EmailOttDeliveryHandler.OTT_INPUT_URL` (`/ott/input`) and `GenerateOneTimeTokenFilter.DEFAULT_GENERATE_URL` (`/ott/generate`); without this, a principal mid-MFA-gate (holding `FACTOR_PASSWORD` but not yet `FACTOR_OTT`) was denied access to the OTT screens themselves by the newly-enforced composed authorization check

**Also fixed this phase (found via live browser testing, not caught by any existing test):**
- `OneTimeTokenConfigurer` no longer calls `.defaultSubmitPageUrl(...)` — that method unconditionally re-invokes `showDefaultSubmitPage(true)` internally, a footgun for call-order regressions
- `OneTimeTokenConfigurer` now calls `.loginPage(...)` with a real value (see below for which one, and why it moved) — without ANY custom login page set, `DefaultLoginPageGeneratingFilter` (Spring's built-in login page) was silently added to Chain 3 (since OTT's own `isCustomLoginPage()` stayed `false`) and intercepted `GET /login` before `LoginController` ever ran
- **`SecurityChains` Chain 1 now registers its own `defaultDeniedHandlerForMissingAuthority` for `FactorGrantedAuthority.OTT_AUTHORITY`, matched by `NegatedRequestMatcher(FormLoginConfigurer.BEARER_TOKEN_MATCHER)` (that field made `public` for this).** `OneTimeTokenConfigurer` registers this same missing-FACTOR_OTT routing automatically on `init()`, but only on the `HttpSecurity` it's applied to — Chain 3. `/oauth2/authorize` lives on **Chain 1**, which never carries the OTT configurer (form login/OTT/SAML2 stay in Chain 3 only, per DESIGN.md), so a principal missing `FACTOR_OTT` there fell through to the default `AccessDeniedHandlerImpl` and got a bare **403** instead of being routed into the OTT flow. Found by backdating `user2`'s `verified_at` past the daily interval and logging in for real — every existing test, including the Phase 2 chain-inventory one, missed this because none of them exercised a denied (not just unauthenticated) request against Chain 1.
- **New page `EmailOttDeliveryHandler.OTT_REQUEST_URL` = `/ott/request`, mapped by `LoginController`, template `ott-request.html`.** The Chain 1 fix above initially pointed the missing-`FACTOR_OTT` entry point at `SecurityConstants.LOGIN_PAGE` — which only renders a username/password form. A principal landing there missing `FACTOR_OTT` has *already* authenticated with a password; resubmitting that form just re-authenticates and redirects straight back into the same denial — an infinite loop, found by trying the fixed flow for real (`GET /login?factor.type=ott&factor.reason=missing` with no way off that page). Both `OneTimeTokenConfigurer.loginPage(...)` and Chain 1's new entry point now point at `/ott/request` instead: a small page that shows the current principal's username (via `#authentication.name`, `thymeleaf-extras-springsecurity6`) and POSTs it to `/ott/generate` on a button click, handing off to the existing `/ott/input` code-entry screen. Also fixed `ott-input.html`'s "Resend" link, which pointed at `/ott/resend` — never a mapped endpoint — to point at `/ott/request` instead, since it's the same "get me a fresh code" action.
- **Introducing `OTT_REQUEST_URL` as `OneTimeTokenConfigurer`'s `loginPage(...)` had a further side effect: it silently reassigned `OneTimeTokenAuthenticationFilter`'s own submission-processing URL too.** `loginPage(...)` calls `updateAuthenticationDefaults()` internally, which — finding the processing URL still unset at that point — virtually dispatches into this configurer's own `loginProcessingUrl(...)` override and points the filter's actual matcher at `OTT_REQUEST_URL` instead of the real endpoint `/login/ott`. (Root cause, traced in the actual Spring Security source: `OneTimeTokenLoginConfigurer`'s constructor passes `null` as `defaultLoginProcessingUrl` to the base class, so the base's `loginProcessingUrl` field starts genuinely `null`; a separate, dead field of the same name on the subclass is eagerly initialized to `/login/ott` but is never read by anything that affects behavior. `OneTimeTokenLoginConfigurer.init()`'s own `if (getLoginProcessingUrl() == null)` default-setting logic never fires, since by the time it runs the field is already non-null.) Codes typed on `/ott/input` (which posts to `/login/ott` per that template) fell through the filter chain completely unvalidated and got denied, bouncing back to `/ott/request`. Fixed by pinning `.loginProcessingUrl(OneTimeTokenAuthenticationFilter.DEFAULT_LOGIN_PROCESSING_URL)` explicitly, after `.loginPage(...)`, in `OneTimeTokenConfigurer`. `OneTimeTokenRepository.findByUsername(String)` added so tests (and anything else) can look up an issued code without a mail server.
- Regression-tested by `Phase4Chain1MissingFactorTests` (full `MockMvc` flow, extended through all three bugs: login → follow the saved-request redirect to `/oauth2/authorize` → assert 3xx landing on `/ott/request`, not `/login` or 403 → `GET /ott/request` → `POST /ott/generate` → assert redirect to `/ott/input` → fetch the issued code from the repository → `POST /login/ott` with it → assert the redirect resumes `/oauth2/authorize`, not another bounce to `/ott/request`).

**Deferred (explicitly, by design, not oversight):**
- `validDuration` demo on `/api/profile/**` — that endpoint lives on Chain 2 (STATELESS JWT resource server), whose `JwtAuthenticationConverter`/factor-claim propagation is Phase 6 work; `AccessTokenCustomizer` today only adds `ROLE_` authorities, not `FACTOR_` ones
- OTT invalidation "on password change" — no password-change feature exists yet to hook into (DESIGN.md only sketches it as a future `CredentialsExpiredException` flow)
- IP policy composition (`AuthorizationManagers.allOf(orgPolicy, ipPolicy)`) — `OrgIpAuthorizationManager` doesn't exist yet; `AuthorizationPolicyConfig` wires `orgPolicy` alone for now, Phase 5 adds the composition

**Tests:** `Phase4AuthorizationPolicyTests` (7 — strictest-wins, interval-satisfied, fail-closed, admin-membership-link, no-qualifying-membership, inactive-membership-ignored, unknown-username-requires-nothing), `Phase4LoginRecordingTests` (2), `Phase4OttAttemptCapTests` (3, unit-level against the handler directly), `Phase4Chain1MissingFactorTests` (1, full `MockMvc` end-to-end). 21 total. Verified on both `spring-security.version=7.1.0` and `=7.1.1` per `PLAN.md`.

Note for anyone adding `@AutoConfigureMockMvc`-based tests in this project: Boot 4.1's modularized test starters moved it — it's `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, not the classic `org.springframework.boot.test.autoconfigure.web.servlet` package.

---

## What's Next

| Phase | Topic | Status |
|-------|-------|--------|
| 5 | IP restriction (`OrgIpAuthorizationManager`) | 🔜 Next |
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
  Tests run: 21, Failures: 0, Errors: 0
  - AuthServerApplicationTests (1) — contextLoads
  - Phase 2: Filter Chains & Terminal Rejections (3)
  - Phase 3: MFA with One-Time Tokens (4)
  - Phase 4: Per-Organization MFA Policy (7)
  - Phase 4: Login recording (2)
  - Phase 4: OTT attempt cap (3)
  - Phase 4: Chain 1 missing-factor routing (1)

resource-server:
  Tests run: 1, Failures: 0, Errors: 0
  - ResourceServerApplicationTests (1) — contextLoads
```

Run with: `cd auth-server && mvn -q test`

---

## Known Gaps / Watch Points

1. **OTT end-to-end spike** — the critical Phase 3 test (saved `/oauth2/authorize` request replayed after OTT) requires running MySQL + Mailpit stack; JUnit tests still verify OTT mechanics only. The login-page/redirect plumbing itself *was* exercised manually against a real running instance during Phase 4 (that's how the `DefaultLoginPageGeneratingFilter` and `defaultSubmitPageUrl` bugs were found) — but not against the full Mailpit-backed stack.
2. **`IdentityChangeAwareSessionStrategy`** — fully wired but only exercised manually (Phase 7 SSO will stress-test it).
3. **`failedAttempts`** (lockout counter) — entity field exists but still not updated on auth failure; `last_login_at` and `user_verification.verified_at` *are* now written, via `LoginRecordingListener` (Phase 4).
4. **`DataInitializer`** — runs once (guarded by checking for `user1`), not on every startup as previously noted here; guarded by `app.data.seed=true` property. Do not enable in production. Its seeded `user_verification` rows are timestamped at first-ever seed time, so a freshly-seeded `user2` (DAILY org) won't be prompted for OTT until that timestamp ages past 24h — expected, not a bug, if you're testing the MFA gate manually right after a first run.
5. **`ddl-auto: update`** — fine for dev/POC; must change to `validate` before any production use.
6. **Chain 2 (`/api/**`) and the org-policy MFA check** — since `AuthorizationPolicyConfig`'s bean is global, it now also composes into Chain 2's `.authenticated()`. For `NEVER`-mode orgs this is a no-op (empty requirement list). For orgs with an active MFA interval, once `verified_at` goes stale mid-token-lifetime, `/api/**` calls will start being denied even though the bearer token hasn't expired, because the JWT-derived authentication doesn't carry `FACTOR_OTT`/`FACTOR_PASSWORD` claims (`AccessTokenCustomizer` only adds `ROLE_` today). Arguably a reasonable defense-in-depth property, but worth knowing about before Phase 6 (`JwtAuthenticationConverter`) addresses it properly.
