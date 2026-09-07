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
| `common-security` | — | — (library jar, not a runnable app) |
| `ui` | 5173 (dev) | — |

`common-security` is a standalone Maven module (own `pom.xml`), **not** a reactor child — `auth-server` and `resource-server` depend on it by Maven coordinates (`com.dkds:common-security:0.0.1-SNAPSHOT`), same as any third-party jar. Build and `mvn install` it to the local repo before building either consumer:
```bash
cd common-security && mvn install -q
cd ../auth-server && mvn test
cd ../resource-server && mvn test
```

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
- `SecurityChains` — Chain 3's `authorizeHttpRequests` grants `/ott/input`, `/ott/request` and `/ott/generate` to principals holding `FACTOR_PASSWORD` (see "OTT endpoints tightened" below); without *some* carve-out here, a principal mid-MFA-gate (holding `FACTOR_PASSWORD` but not yet `FACTOR_OTT`) would be denied access to the OTT screens themselves by the newly-enforced composed authorization check

**Also fixed this phase (found via live browser testing, not caught by any existing test):**
- `OneTimeTokenConfigurer` no longer calls `.defaultSubmitPageUrl(...)` — that method unconditionally re-invokes `showDefaultSubmitPage(true)` internally, a footgun for call-order regressions
- `OneTimeTokenConfigurer` now calls `.loginPage(...)` with a real value (see below for which one, and why it moved) — without ANY custom login page set, `DefaultLoginPageGeneratingFilter` (Spring's built-in login page) was silently added to Chain 3 (since OTT's own `isCustomLoginPage()` stayed `false`) and intercepted `GET /login` before `LoginController` ever ran
- **`SecurityChains` Chain 1 now registers its own `defaultDeniedHandlerForMissingAuthority` for `FactorGrantedAuthority.OTT_AUTHORITY`, matched by `NegatedRequestMatcher(FormLoginConfigurer.BEARER_TOKEN_MATCHER)` (that field made `public` for this).** `OneTimeTokenConfigurer` registers this same missing-FACTOR_OTT routing automatically on `init()`, but only on the `HttpSecurity` it's applied to — Chain 3. `/oauth2/authorize` lives on **Chain 1**, which never carries the OTT configurer (form login/OTT/SAML2 stay in Chain 3 only, per DESIGN.md), so a principal missing `FACTOR_OTT` there fell through to the default `AccessDeniedHandlerImpl` and got a bare **403** instead of being routed into the OTT flow. Found by backdating `user2`'s `verified_at` past the daily interval and logging in for real — every existing test, including the Phase 2 chain-inventory one, missed this because none of them exercised a denied (not just unauthenticated) request against Chain 1.
- **New page `EmailOttDeliveryHandler.OTT_REQUEST_URL` = `/ott/request`, mapped by `LoginController`, template `ott-request.html`.** The Chain 1 fix above initially pointed the missing-`FACTOR_OTT` entry point at `SecurityConstants.LOGIN_PAGE` — which only renders a username/password form. A principal landing there missing `FACTOR_OTT` has *already* authenticated with a password; resubmitting that form just re-authenticates and redirects straight back into the same denial — an infinite loop, found by trying the fixed flow for real (`GET /login?factor.type=ott&factor.reason=missing` with no way off that page). Both `OneTimeTokenConfigurer.loginPage(...)` and Chain 1's new entry point now point at `/ott/request` instead: a small page that shows the current principal's username (via `#authentication.name`, `thymeleaf-extras-springsecurity6`) and POSTs it to `/ott/generate` on a button click, handing off to the existing `/ott/input` code-entry screen. Also fixed `ott-input.html`'s "Resend" link, which pointed at `/ott/resend` — never a mapped endpoint — to point at `/ott/request` instead, since it's the same "get me a fresh code" action.
- **Introducing `OTT_REQUEST_URL` as `OneTimeTokenConfigurer`'s `loginPage(...)` had a further side effect: it silently reassigned `OneTimeTokenAuthenticationFilter`'s own submission-processing URL too.** `loginPage(...)` calls `updateAuthenticationDefaults()` internally, which — finding the processing URL still unset at that point — virtually dispatches into this configurer's own `loginProcessingUrl(...)` override and points the filter's actual matcher at `OTT_REQUEST_URL` instead of the real endpoint `/login/ott`. (Root cause, traced in the actual Spring Security source: `OneTimeTokenLoginConfigurer`'s constructor passes `null` as `defaultLoginProcessingUrl` to the base class, so the base's `loginProcessingUrl` field starts genuinely `null`; a separate, dead field of the same name on the subclass is eagerly initialized to `/login/ott` but is never read by anything that affects behavior. `OneTimeTokenLoginConfigurer.init()`'s own `if (getLoginProcessingUrl() == null)` default-setting logic never fires, since by the time it runs the field is already non-null.) Codes typed on `/ott/input` (which posts to `/login/ott` per that template) fell through the filter chain completely unvalidated and got denied, bouncing back to `/ott/request`. Fixed by pinning `.loginProcessingUrl(OneTimeTokenAuthenticationFilter.DEFAULT_LOGIN_PROCESSING_URL)` explicitly, after `.loginPage(...)`, in `OneTimeTokenConfigurer`. `OneTimeTokenRepository.findByUsername(String)` added so tests (and anything else) can look up an issued code without a mail server.
- Regression-tested by `Phase4Chain1MissingFactorTests` (full `MockMvc` flow, extended through all three bugs: login → follow the saved-request redirect to `/oauth2/authorize` → assert 3xx landing on `/ott/request`, not `/login` or 403 → `GET /ott/request` → `POST /ott/generate` → assert redirect to `/ott/input` → fetch the issued code from the repository → `POST /login/ott` with it → assert the redirect resumes `/oauth2/authorize`, not another bounce to `/ott/request`).

**Post-Phase-4 hardening (requested follow-up, not a bug report):**
- **OTT endpoints tightened from `permitAll()` to requiring `FACTOR_PASSWORD`.** Naively swapping in the DSL's own `.hasAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)` would have reintroduced the exact deadlock `permitAll()` was there to avoid: `AuthorizedUrl.hasAuthority(...)` (and every `AuthorizedUrl` method except `permitAll()`/`denyAll()`/`anonymous()`) is built via the same `AuthorizationManagerFactory` bean from `AuthorizationPolicyConfig`, which ANDs in `RequiredAuthoritiesAuthorizationManager` (`setAdditionalAuthorization(...)`) — so it would once again demand `FACTOR_OTT` on the very pages that exist to obtain it. Verified against `DefaultAuthorizationManagerFactory` source: `withAdditionalAuthorization()` composes on every path except those three. Fixed by using `AuthorizedUrl.access(AuthorizationManager)` instead, handing it a plain `AuthorityAuthorizationManager.hasAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)` built directly — bypasses the factory (and its composition) entirely, while still requiring a real password-authenticated principal, not just `permitAll()`.
- **`/ott/generate` was never actually protected by `authorizeHttpRequests` at all, in either the old or the naively-fixed version — a real hole.** `GenerateOneTimeTokenFilter` (registered by `OneTimeTokenLoginConfigurer`) sits *ahead* of `AuthorizationFilter` in the chain (confirmed from the logged filter order: `..., GenerateOneTimeTokenFilter, UsernamePasswordAuthenticationFilter, ..., AnonymousAuthenticationFilter, ..., AuthorizationFilter`) and — via the default `DefaultGenerateOneTimeTokenRequestResolver` — trusts a bare `username` request parameter with no authentication check whatsoever, unconditionally minting and emailing a real code. Any anonymous caller could `POST /ott/generate?username=<anyone>` directly and get a code emailed for an arbitrary account, regardless of what `authorizeHttpRequests` said. New `AuthenticatedGenerateOneTimeTokenRequestResolver` (`onetimetoken` package) replaces it, wired via `OneTimeTokenLoginConfigurer.generateRequestResolver(...)` in `OneTimeTokenConfigurer`: it ignores the `username` parameter and resolves the request from `SecurityContextHolder`'s current `Authentication` instead (`null`/unauthenticated/`AnonymousAuthenticationToken` → returns `null`, which makes the filter fall through to the rest of the chain — landing on `AuthorizationFilter` and getting denied like anything else). Caught by a new negative test, `Phase4OttEndpointsRequirePasswordTests`, after the naive `authorizeHttpRequests`-only fix passed every existing test but still let an anonymous `POST /ott/generate` through.
- **`FormLoginConfigurer` is now a `@Bean`** (new `login/LoginConfig.java`), matching how `OneTimeTokenConfigurer` is already wired, instead of being `new`'d inline in `SecurityChains`. Not the "don't declare `Customizer<HttpSecurity>` beans" case DESIGN.md warns about — that's about the functional `Customizer<HttpSecurity>` type, which Spring auto-applies ambiently to every chain being built; `FormLoginConfigurer`'s own type is never auto-detected that way, it only takes effect where `SecurityChains` explicitly does `http.with(formLoginConfigurer, ...)`.
- **Login with no saved request to resume used to 404.** Found via live testing: log out, then log back in at `/login` directly (no prior `/oauth2/authorize` hit in the new session — the old saved request was cleared when logout invalidated the session). `formLogin()` had never set a `defaultSuccessUrl`, so `SavedRequestAwareAuthenticationSuccessHandler` fell back to its own built-in default target, `/` — which this auth-server-only app never maps (no root controller), landing on Spring Boot's Whitelabel `/error` page with a 404. Fixed by `FormLoginConfigurer` now calling `.defaultSuccessUrl(SecurityConstants.LOGIN_SUCCESS_URL)` (the one-arg overload, `alwaysUse=false`) — a real saved request, i.e. the normal SPA flow through `/oauth2/authorize`, still wins and gets resumed exactly as before; this only changes what happens when there's nothing to resume. New page `/login-success`, mapped by `LoginController`, template `login-success.html` — a small "you're signed in" card, since this auth server has no dashboard of its own.

Regression-tested by `Phase4OttEndpointsRequirePasswordTests` (3 — anonymous `GET /ott/request`, `GET /ott/input`, and `POST /ott/generate` with an arbitrary username all redirect to `/login` instead of succeeding) and `Phase4LoginSuccessFallbackTests` (1 — login with no saved request redirects to `/login-success`, not `/`).

**Deferred (explicitly, by design, not oversight):**
- `validDuration` demo on `/api/profile/**` — that endpoint lives on Chain 2 (STATELESS JWT resource server), whose `JwtAuthenticationConverter`/factor-claim propagation is Phase 6 work; `AccessTokenCustomizer` today only adds `ROLE_` authorities, not `FACTOR_` ones
- OTT invalidation "on password change" — no password-change feature exists yet to hook into (DESIGN.md only sketches it as a future `CredentialsExpiredException` flow)
- IP policy composition (`AuthorizationManagers.allOf(orgPolicy, ipPolicy)`) — done in Phase 5, see below

**Tests:** `Phase4AuthorizationPolicyTests` (7 — strictest-wins, interval-satisfied, fail-closed, admin-membership-link, no-qualifying-membership, inactive-membership-ignored, unknown-username-requires-nothing), `Phase4LoginRecordingTests` (2), `Phase4OttAttemptCapTests` (3, unit-level against the handler directly), `Phase4Chain1MissingFactorTests` (1, full `MockMvc` end-to-end), `Phase4OttEndpointsRequirePasswordTests` (3, anonymous access denied), `Phase4LoginSuccessFallbackTests` (1, login-with-nothing-saved fallback). 25 total. Verified on both `spring-security.version=7.1.0` and `=7.1.1` per `PLAN.md`.

Note for anyone adding `@AutoConfigureMockMvc`-based tests in this project: Boot 4.1's modularized test starters moved it — it's `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, not the classic `org.springframework.boot.test.autoconfigure.web.servlet` package.

---

### ✅ Phase 5 — IP Restriction

**Package: `authorization/`**
- `OrgIpAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext>` — for the current principal's active memberships, any org with `ip_restriction_enabled=true` must have the request's client address (`request.getRemoteAddr()`) fall inside at least one of its `org_ip_range` CIDR blocks (`IpAddressMatcher`, one framework-provided instance per range — no hand-rolled CIDR math). A single restricted org whose ranges don't cover the address denies the whole request; this is an AND across every restricted org on the user's memberships, not "any org's ranges will do." `PLATFORM_ADMIN` (checked via `ROLE_PLATFORM_ADMIN`, reconstructed locally as `"ROLE_" + UserRole.PLATFORM_ADMIN` rather than importing `SecurityConstants` — `authorization` may not depend on `security`, same rule `OrgPolicyRequiredAuthoritiesRepository` already follows) is exempt, checked first. Unknown/anonymous username → permit, same reasoning as `OrgPolicyRequiredAuthoritiesRepository`: this manager runs before the base `authenticated()` check for every request, including unauthenticated ones, so it must defer rather than fail-closed-deny anonymous traffic. Any other exception → fail closed (deny).
- `AuthorizationPolicyConfig` now composes `AuthorizationManagers.allOf(ottPolicy, ipAuthorizationManager)` into `additionalAuthorization`, exactly as DESIGN.md specifies. `allOf` short-circuits on the first denial and returns that manager's own `AuthorizationResult` unwrapped — since `OrgIpAuthorizationManager` returns a plain `AuthorizationDecision(false)` (not `AuthorityAuthorizationDecision`/`FactorAuthorizationDecision`), an IP denial falls straight through to the ordinary `AccessDeniedHandlerImpl` (plain 403), never getting misrouted into `OneTimeTokenConfigurer`'s missing-`FACTOR_OTT` redirect handling — verified against `DelegatingMissingAuthorityAccessDeniedHandler` source, which only intercepts those two specific `AuthorizationResult` subtypes.

**Package: `user/`**
- **New `UserRole` enum** (`MEMBER`, `PLATFORM_ADMIN`) and `AppUser.role` column (`@Builder.Default MEMBER`), because this didn't exist before Phase 5 and was needed for the admin exemption. Found along the way: `AppUserDetailsService` had `.roles("MEMBER")` **hardcoded** for every user — the `admin` account `DataInitializer` seeds was never actually granted `ROLE_PLATFORM_ADMIN` at all, despite `SecurityConstants.ROLE_PLATFORM_ADMIN` existing unused since Phase 2. Fixed to `.roles(user.getRole().name())`; `DataInitializer`'s admin user now sets `.role(UserRole.PLATFORM_ADMIN)` explicitly.

**Trusted proxies / `ForwardedHeaderFilter` — deliberately NOT registered.** DESIGN.md says to register Spring's `ForwardedHeaderFilter` "with trusted proxies" so `getRemoteAddr()` reflects the real client behind a reverse proxy. In practice that filter has no proxy allowlist of its own — once active it unconditionally trusts whatever `X-Forwarded-For` a caller sends, full stop. This stack has no reverse proxy in front of `auth-server` (exposed directly on :9000), so enabling it unconditionally would let any direct caller spoof past IP restriction entirely, directly contradicting PLAN.md's own required test 4 ("spoofed X-Forwarded-For not trusted"). Asked the user how to resolve this rather than guessing; confirmed: leave `server.forward-headers-strategy` at Boot's default (`none`) everywhere in this repo (explicit in `application-test.yaml`, documented at length in `SecurityConfig`), so `OrgIpAuthorizationManager`'s `getRemoteAddr()` reads are spoof-proof today. Flip it to `framework` in whatever profile actually deploys this behind a real trusted proxy.

**Tests:** `Phase5IpRestrictionTests` (5 — in-range permitted, out-of-range denied, `PLATFORM_ADMIN` exempt from an out-of-range address, a non-admin still denied from that same address, spoofed `X-Forwarded-For` has no effect on the real `/oauth2/authorize` redirect path). All fixtures use RFC 5737 TEST-NET ranges (`203.0.113.0/24` in-range, `198.51.100.0/24` out-of-range) so nothing collides with a real routable address. 30 total tests. Verified on both `spring-security.version=7.1.0` and `=7.1.1`.

**Known extension worth noting:** since `AuthorizationPolicyConfig`'s `AuthorizationManagerFactory` bean is global, `OrgIpAuthorizationManager` now also composes into Chain 2's (`/api/**`) `.authenticated()` check, same as the OTT policy manager already did (see Known Gap 6 below) — a bearer-token call from an IP-restricted org's member now also needs to originate from an allowed address, or it gets denied even with a perfectly valid, unexpired JWT. Not covered by any test yet (Chain 2 has none exercising `/api/**` end-to-end); worth keeping in mind for Phase 6.

---

### ✅ Phase 6 — Resource Server + `common-security` Module + JWT Validation

**Structural decision, asked up front rather than guessed:** PLAN.md's Phase 0 called for a Maven reactor with `common-security` as a child module, but that was never built — `auth-server` and `resource-server` have been two fully independent standalone Maven projects since Phase 0, undocumented as a deviation until now. Rather than silently duplicate the shared JWT resource-server config into both apps, or silently retrofit a full reactor (a much bigger structural change touching every existing `pom.xml`), asked the user: created `common-security` as its own standalone Maven module (own `pom.xml`, `mvn install`ed to the local repo), consumed by both apps as an ordinary Maven dependency. Smallest change that still gets a genuinely shared, independently-testable module.

**New module: `common-security`** (`com.dkds.commonsecurity`)
- `RolesAndScopesJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>>` — merges Spring's own default scope-derived authorities (`SCOPE_*`, from the standard `scope`/`scp` claim) with a second `JwtGrantedAuthoritiesConverter` configured for a `roles` claim → `ROLE_*`. Its `ROLES_CLAIM_NAME`/`ROLE_AUTHORITY_PREFIX` constants are the explicit, compile-time-checked contract between the token issuer (auth-server's `AccessTokenCustomizer`) and every consumer (auth-server's own Chain 2, and any resource server) — both ends reference the same constants instead of each hardcoding the literal string `"roles"`.
- `ResourceServerSecurityConfig` — the **complete** chain for a stateless JWT resource server with no other security concerns: `SessionCreationPolicy.STATELESS`, CSRF off, CORS on (looks up a `CorsConfigurationSource` bean from whichever app imports it), `anyRequest().authenticated()`, `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` wired to a `JwtAuthenticationConverter` bean built from the converter above. Published as a full `SecurityFilterChain` **bean**, not a `Customizer<HttpSecurity>` one — deliberately, per PLAN.md's own test 3 (below) and the same footgun already documented on `FormLoginConfigurer`/`OneTimeTokenConfigurer`: a `Customizer<HttpSecurity>` bean gets applied ambiently to *every* chain being built anywhere in the importing app's context, which would be actively wrong here.
- `auth-server` does **not** `@Import` `ResourceServerSecurityConfig` — that would add a fourth chain on top of its existing three and break Phase 2's "exactly three chains" test. It depends on `common-security` only for the reusable converter class, wiring its own `JwtAuthenticationConverter` bean directly in `token/JwkConfig.java` and passing it into Chain 2's `.oauth2ResourceServer(...)`. `resource-server` has no other security concerns, so it `@Import`s `ResourceServerSecurityConfig` wholesale from `ResourceServerApplication`.

**Real bug found and fixed: access tokens carried no role information at all.** `AccessTokenCustomizer` only ever enriched the **ID token** (`if (OidcParameterNames.ID_TOKEN.equals(...))`) — the **access token**, which is the only thing any resource server or Chain 2 actually receives as a Bearer token, carried nothing beyond the standard registered claims and whatever scopes were requested. Every `JwtAuthenticationConverter` anywhere in this codebase was therefore only ever going to be able to derive `SCOPE_*` authorities, never `ROLE_*` — "realistic access-token claim set" (PLAN.md's own Phase 6 wording) didn't exist before this phase. Fixed by widening the customizer's guard to also fire for `OAuth2TokenType.ACCESS_TOKEN`.

**Package `resource-server/`** (previously just a bare `@SpringBootApplication` + a `ResourceServerApplicationTests` that didn't even compile a working context — see below)
- `ProfileController` — `GET /api/profile`, a minimal demo endpoint that echoes back the validated JWT's `sub`/`roles` claims plus the actually-granted authorities, proving the realistic claim set reaches a real controller.
- `CorsConfig` — same shape as auth-server's own, allowing `localhost:5173` (the SPA).
- `application.yaml`/`application-devpod.yaml` — added `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://localhost:9000/oauth2/jwks`. Both auth-server and resource-server always run as normal host processes in every profile this repo has (only the databases are dockerized), so `localhost:9000` is correct regardless of profile.

**Also fixed this phase (pre-existing, not introduced by it):** `resource-server`'s test suite has been broken since Phase 0 — `ResourceServerApplicationTests` had no `test` profile, no H2 dependency, and a MySQL-only `application.yaml`, so `mvn test` failed to even build the `ApplicationContext` (confirmed by running it before making any change here: `HibernateException: Unable to determine Dialect without JDBC metadata`). Added the H2 test dependency and `application-test.yaml` (mirrors auth-server's own pattern exactly) and `@ActiveProfiles("test")` on the test class.

**Tests (resource-server):**
- `Phase6ChainInventoryTests` (2) — PLAN.md test 1 verbatim: exactly one filter chain, and no `UsernamePasswordAuthenticationFilter` anywhere in it.
- `Phase6NoAmbientCustomizerBeanTests` (1) — PLAN.md test 3 verbatim: no bean of type `Customizer<HttpSecurity>` exists anywhere in resource-server's context (which now includes everything `common-security` contributes via `@Import`). This is what actually enforces the `SecurityFilterChain`-not-`Customizer` design choice above, not just documentation of it.
- `Phase6ProfileEndpointTests` (2) — stands in for PLAN.md test 2 ("end-to-end call from portal-ui through to resource-server"). A literal cross-process test (a live auth-server minting a real signed token, a live resource-server validating it over the network) is a manual verification step, same precedent as Phase 3's Mailpit-backed OTT flow (Known Gap 1) — there's no live-stack automation anywhere else in this repo either. What's automated instead, and is the substantive thing "end-to-end" is actually checking: `SecurityMockMvcRequestPostProcessors.jwt()` configured with a realistic `roles`+`scope` claim set, with authorities derived via the **real** `JwtAuthenticationConverter` bean pulled from context (not a fresh hand-built instance) — proving the actual wired-up chain, converter, and controller all agree, not just a unit test of the converter class in isolation. Includes the phase's negative test: no bearer token at all → 401.
- `ResourceServerApplicationTests` (1) — now passes (see above).
- 6 total. Verified on both `spring-security.version=7.1.0` and `=7.1.1` (`common-security`'s jar, compiled once against 7.1.0, confirmed binary-compatible when the consumer resolves 7.1.1 at its own build time — no need to rebuild it per version).

**Phase 6 follow-up: shared static signing key, no more JWKS endpoint.** User asked whether the RSA key pair could be shared via `common-security` so resource servers don't need to fetch JWKS over the network. Implemented, but shared only the **public** half — sharing the private key with `resource-server` would let it forge tokens as the authorization server, which was never actually the ask (the ask was "avoid the network round-trip," not "give resource-server signing power").
- `common-security/src/main/resources/keys/jwt-public-key.pem` — the shared RSA public key, packaged inside the jar so any consumer gets it automatically just by depending on the module. New `PemUtils` (dependency-free, plain `java.security` APIs) parses PKCS8-private/X.509-public PEM.
- `ResourceServerSecurityConfig` now publishes its own `JwtDecoder` bean (`@ConditionalOnMissingBean`, so a consumer can still override) built directly from that shared public key via `NimbusJwtDecoder.withPublicKey(...)` — no `jwk-set-uri`, no network dependency on auth-server being reachable at validation time. Removed the now-unnecessary `spring.security.oauth2.resourceserver.jwt.*` properties from all of `resource-server`'s profiles.
- **The private key stays auth-server-only** — `auth-server/src/main/resources/keys/jwt-signing-key.pem`, never added to `common-security`. `JwkConfig.keyPair()` was rewritten from "generate a random 2048-bit RSA pair on every boot" to "load this fixed private key and derive its public half from the loaded key's own CRT modulus/exponent" (`RSAPrivateCrtKey.getModulus()`/`getPublicExponent()` — not a second read of the PEM file). At startup it also verifies that derived public key equals `common-security`'s shared copy byte-for-byte, and fails fast (not a resource server quietly rejecting every real token later) if the two files are ever regenerated independently and drift apart.
- **Fixes a real pre-existing fragility, not just adds a feature:** the old random-per-boot key meant every auth-server restart silently invalidated every issued token and rotated the signing key out from under any resource server that had cached the old JWKS response. A fixed key removes that whole class of transient failure — though it also means there is now no key-rotation story at all, which is fine for this POC/lab but would be a real gap in any actual deployment (see Known Gaps).
- **Known POC-only shortcut, flagged, not hidden:** a real RSA private key is checked into `auth-server/src/main/resources/keys/jwt-signing-key.pem`. Fine for a lab; never acceptable in a real deployment — see Known Gaps.
- New test `SharedSigningKeyRoundTripTests` (auth-server, 2) — the actual proof this works, not just that it compiles: signs a real token with auth-server's real private key (the `KeyPair` bean from context), decodes it using *only* the shared public key file, built the exact same way `ResourceServerSecurityConfig`'s own `JwtDecoder` bean builds it. Negative case: a token signed with a *different*, freshly-generated key is rejected — proving the shared key genuinely pins the trusted signer rather than any RS256 signature passing.
- Total auth-server tests: 32. resource-server: still 6 (unaffected — its tests use `SecurityMockMvcRequestPostProcessors.jwt()`, which never touches the real `JwtDecoder` either way). Verified on both `spring-security.version=7.1.0` and `=7.1.1`.

---

### ✅ Phase 7 — SAML2 SP-initiated SSO

- `sso/Saml2Configurer`, `SsoConfig`, `DatabaseRelyingPartyRegistrationRepository`, `SamlUserAuthoritiesConverter` — Keycloak (`lab` realm) as the test IdP, exported to `auth-server/docker/keycloak/lab-realm.json` so it's reproducible via `--import-realm`.
- Terminal-rejection checks (disabled/locked/no-membership) apply on the SAML path too, same as form login — `Phase7SamlTerminalRejectionTests`.
- `FACTOR_SAML_RESPONSE` alone satisfies the MFA gate — `Phase7SamlFactorSatisfiesGateTests`.
- `sso/SsoDiscoveryController` (`/sso/discover`) lets the login page offer SSO without the caller hardcoding a `registrationId` — `Phase7SsoDiscoveryTests`.

### ✅ Phase 8 — IdP-initiated flow + identity change

- `sso/AssertionReplayGuard` (persisted `SeenSamlAssertion` IDs — an unsolicited assertion has no `InResponseTo`, so this is the replay defence), `RelayStateValidator` (host allowlist), `IdpInitiatedSuccessHandler` (no saved request → redirect to the SPA landing route, which starts a fresh authorization-code+PKCE request).
- `security/IdentityChangeAwareSessionStrategy` — clears the saved request only when the incoming principal differs from the current one; wired via `SessionRequestCacheConfig`.
- Tests: `Phase8AssertionReplayGuardTests`, `Phase8RelayStateValidatorTests`, `Phase8IdpInitiatedSuccessHandlerTests`, `Phase8IdentityChangeSessionStrategyTests`.

### ✅ Phase 9 — Captcha

- `login/CaptchaFilter` — plain `OncePerRequestFilter` before `UsernamePasswordAuthenticationFilter`, Chain 3 only (`Phase9ChainInventoryTests`).
- `login/CaptchaService` — `isRequired(username, ip)` gates on recent `login_attempt` failures (`CREDENTIAL_FAILURE_THRESHOLD = 3` within a 15-minute lookback); `verify(token)` is a deliberate lab stand-in accepting any non-blank value except the reserved sentinel `"wrong"`; `recordCaptchaFailure(username)` writes `AppUser.failedAttempts`/`lockedUntil`, locking for 15 minutes at `CAPTCHA_FAILURE_THRESHOLD = 3`.
- `login/LoginAttemptRecordingListener` — writes `login_attempt` for every password attempt (success and failure), the signal `isRequired()` reads.
- **2026-09-07 follow-up, this session — three real bugs found and fixed against the actually-running app** (none were caught by the test suite at the time; regression tests weren't added back for these — see the note at the very end of this file if that changes): a `login.html` template crash on every plain `/login` load, a Thymeleaf `param.containsKey(...)` trap that made both captcha messages permanently show, and a missing-lockout gap where a wrong password submitted alongside an already-valid captcha token never counted toward the same lockout a wrong captcha token does. Full writeup at the end of this file under "Session notes."

### ✅ Phase 10 — IdP MFA mapping

- SAML response's `AuthnContextClassRef` maps onto `FACTOR_IDP_MFA` in the response authentication converter, satisfying org policy without an OTT prompt.
- Tests: `Phase10AuthnContextMfaMappingTests`, `Phase10IdpMfaSatisfiesOrgPolicyTests`, `Phase10UnrecognizedSamlIdentityLoggingTests`.

### ✅ Phase 11 — ArchUnit

- `Phase11ArchitectureTests` enforces DESIGN.md's four dependency rules (login/onetimetoken/sso must not depend on authorization; authorization may depend on organization; security may depend on anything and nothing depends on it; common depends on nothing inside the application).

### Also added, beyond the original PLAN.md

- **Remember-me** — `login/RememberMeToken`, `JpaPersistentTokenRepository`, `LoginConfig.rememberMeServices` (`PersistentTokenBasedRememberMeServices`). Chain 3 only, deliberately not Chain 1 — verified live that Spring Authorization Server's own filters run before `RememberMeAuthenticationFilter` ever gets a chance on `/oauth2/authorize`. `RememberMeTests`.
- **resource-server `TasksEndpointTests`** — an `/api/**` endpoint beyond the `/api/profile` example Phase 6 documented above; not otherwise written up here yet.

---

## What's Next

| Phase | Topic | Status |
|-------|-------|--------|
| 5 | IP restriction (`OrgIpAuthorizationManager`) | ✅ Done |
| 6 | Resource server + `common-security` module + JWT validation | ✅ Done |
| 7 | SAML2 SP-initiated SSO via Keycloak | ✅ Done |
| 8 | IdP-initiated flow + identity change strategy | ✅ Done |
| 9 | Captcha filter | ✅ Done (see 2026-09-07 follow-up above/below) |
| 10 | IdP MFA mapping (`AuthnContextClassRef` → `FACTOR_IDP_MFA`) | ✅ Done |
| 11 | ArchUnit enforcement | ✅ Done |

All PLAN.md phases (0–11) are implemented. Remaining open items are the "Known Gaps / Watch Points" below, not unstarted phases.

---

## Test Status

Last verified 2026-09-07 (this session, with the captcha fixes below applied):

```
auth-server:
  Tests run: 84, Failures: 0, Errors: 0
  - AuthServerApplicationTests (1) — contextLoads
  - Phase 2: Filter Chains & Terminal Rejections (3)
  - Phase 3: MFA with One-Time Tokens (4)
  - Phase 4: total (17) — Authorization Policy (7), Chain 1 missing-factor routing (1),
    Login recording (2), form login falls back to /login-success not / (1),
    OTT attempt cap (3), OTT endpoints require FACTOR_PASSWORD (3)
  - Phase 5: IP restriction (5)
  - Phase 7: total (8) — SAML factor satisfies gate (1), SAML terminal rejection (4),
    SSO discovery (3)
  - Phase 8: total (15) — Assertion replay guard (2), RelayState validator (6),
    IdP-initiated success handler (4), Identity-change session strategy (3)
  - Phase 9: total (18) — CaptchaFilter end-to-end (3), CaptchaService (10),
    chain inventory (1), LoginAttemptRecordingListener (4)
  - Phase 10: total (6) — AuthnContext MFA mapping (3), IdP MFA satisfies org policy (2),
    unrecognized SAML identity logging (1)
  - Phase 11: ArchUnit (3)
  - RememberMeTests (2)
  - Shared signing key: no-JWKS round trip (2)

resource-server:
  Tests run: 8, Failures: 0, Errors: 0
  - ResourceServerApplicationTests (1) — contextLoads
  - Phase 6: resource-server chain inventory (2)
  - Phase 6: common-security publishes no ambient Customizer<HttpSecurity> bean (1)
  - Phase 6: /api/profile — realistic access-token claims (2)
  - TasksEndpointTests (2) — not written up elsewhere in this file yet
```

Run with:
```bash
cd common-security && mvn install -q   # must run first — see Project Overview
cd ../auth-server && mvn -q test
cd ../resource-server && mvn -q test
```

---

## Known Gaps / Watch Points

1. **OTT end-to-end spike** — the critical Phase 3 test (saved `/oauth2/authorize` request replayed after OTT) requires running MySQL + Mailpit stack; JUnit tests still verify OTT mechanics only. The login-page/redirect plumbing itself *was* exercised manually against a real running instance during Phase 4 (that's how the `DefaultLoginPageGeneratingFilter` and `defaultSubmitPageUrl` bugs were found) — but not against the full Mailpit-backed stack.
2. **`IdentityChangeAwareSessionStrategy`** — fully wired but only exercised manually (Phase 7 SSO will stress-test it).
3. ~~**`failedAttempts`** (lockout counter) — entity field exists but still not updated on auth failure~~ — **resolved as of Phase 9 / the 2026-09-07 follow-up.** `CaptchaService.recordCaptchaFailure(username)` now writes it from two places: `CaptchaFilter` (wrong captcha token) and `CaptchaAwareAuthenticationFailureHandler` (wrong password submitted alongside an already-valid captcha token — see "Session notes" at the end of this file). `last_login_at` and `user_verification.verified_at` are written via `LoginRecordingListener` (Phase 4), unaffected.
4. **`DataInitializer`** — runs once (guarded by checking for `user1`), not on every startup as previously noted here; guarded by `app.data.seed=true` property. Do not enable in production. Its seeded `user_verification` rows are timestamped at first-ever seed time, so a freshly-seeded `user2` (DAILY org) won't be prompted for OTT until that timestamp ages past 24h — expected, not a bug, if you're testing the MFA gate manually right after a first run.
5. **`ddl-auto: update`** — fine for dev/POC; must change to `validate` before any production use.
6. **Chain 2 (`/api/**`) and the composed org-policy checks** — since `AuthorizationPolicyConfig`'s bean is global, it now also composes into Chain 2's `.authenticated()`, for both halves: the OTT policy manager and (as of Phase 5) `OrgIpAuthorizationManager`. For `NEVER`-mode/non-IP-restricted orgs this is a no-op. For orgs with an active MFA interval, once `verified_at` goes stale mid-token-lifetime, `/api/**` calls will start being denied even though the bearer token hasn't expired, because the JWT-derived authentication doesn't carry `FACTOR_OTT`/`FACTOR_PASSWORD` claims — and this is intentional, not something Phase 6 was meant to close: `FACTOR_*` authorities are session-scoped (DESIGN.md: "issued by Spring Security on authentication"), and baking them into a portable bearer token would let a stolen/replayed token claim a factor it never actually satisfied. Phase 6 gave the access token `ROLE_*` claims, deliberately not `FACTOR_*` ones. Likewise, a bearer-token call from an IP-restricted org's member now also needs to originate from an allowed address. Neither is covered by a Chain-2-specific test yet.
7. **A real RSA private key is checked into the repo** — `auth-server/src/main/resources/keys/jwt-signing-key.pem`, added in the Phase 6 follow-up that moved from a random-per-boot key to a fixed one shared (public half only) via `common-security`. Standard practice for a Spring Security sample/lab, never acceptable for a real deployment — a production build must load it from a real secret store, not a file in source control, and needs an actual key-rotation story (the fixed key traded that away entirely — see the Phase 6 follow-up writeup above).
8. **The captcha/lockout fixes below (2026-09-07) have no regression tests yet.** All three were found and fixed by hand against the live app (`login.html` template + `CaptchaAwareAuthenticationFailureHandler`); the existing 84 auth-server tests were re-run and still pass (they never exercised these paths), but nothing new asserts them. Worth adding before touching `login.html` or the captcha classes again: (a) `GET /login` with no query string renders without error, (b) a wrong-password-with-valid-captcha-token retry redirects to `?error&captchaRequired` and the field stays visible, (c) three such attempts lock the account the same as three wrong-captcha-token attempts do.

---

## Session notes — 2026-09-07: captcha/login-page fixes

Found and fixed live against the running app (IntelliJ on host, containers via the devcontainer's compose stack), not from reading code alone. Recorded here because none of them were caught by the existing test suite and the root causes are non-obvious enough to be worth not re-deriving next time.

**1. `EL1001E: cannot convert from null to boolean` on `GET /login`.** `login.html` had `th:if="${param.captcha and !param.containsKey('error')}"`. Thymeleaf's `param` map (`WebEngineContext.RequestParameterMap`) returns a plain `null` from `.get(key)` — not an empty list — for a missing request parameter, same as `HttpServletRequest.getParameterValues()`. A bare `th:if="${param.captcha}"` tolerates that fine (Thymeleaf does its own null-is-false coercion on the whole expression's *final* result), but SpEL's `and` operator evaluates each operand to a real `Boolean` *internally*, and converting `null` to primitive `boolean` throws. This crashed on essentially every plain `/login` load or `?error` bounce (no `captcha` param present) — not an edge case.

**2. Fixing #1 with `param.containsKey(...)` silently broke the messages instead of the page.** `RequestParameterMap.containsKey(Object)` is hard-coded to `return true` always, by Thymeleaf's own design (see its source: the comment explains it exists only so `param.xxx` property navigation doesn't throw via Spring's `MapAccessor` on a missing key — it was never meant to answer "is this key present"). Using it made `param.containsKey('captcha') and param.containsKey('error')` evaluate `true and true` on literally every request, so "Verification Required"/"Verification Failed" showed permanently regardless of the actual query string. **Correct fix:** compare the *value* to `null` directly — `param.captcha != null` — which routes through `.get()` (correctly implemented) rather than `.containsKey()` (not). Verified against the actual pinned `thymeleaf-3.1.5.RELEASE` sources before trusting this, not guessed.

**3. Wrong password + already-valid captcha token never counted toward the lockout.** `CaptchaService.recordCaptchaFailure(username)` (writes `AppUser.failedAttempts`/`lockedUntil`) was only ever called from `CaptchaFilter`, and only on a *wrong captcha token*. Once past the gate with one valid (any non-blank, non-`"wrong"` — this lab's `verify()` is a stand-in) token, wrong-password retries fell through to Spring Security's default failure handler (`/login?error`, no captcha param at all — which also meant the field disappeared, forcing an extra round trip through `CaptchaFilter`'s informational bounce each time). Since this lab's captcha accepts any non-blank value, that path was effectively unlimited-attempt: an attacker just needed one dummy token to keep resending. New `login/CaptchaAwareAuthenticationFailureHandler`, wired into `FormLoginConfigurer.formLogin(...).failureHandler(...)`: if the failed request carried a non-blank `captchaToken`, it (a) redirects to `?error&captchaRequired` (a query param *deliberately separate* from `CaptchaFilter`'s own `?captcha`, so `login.html` shows the plain "Login Failed" message rather than misattributing it to "Verification Failed") so the field stays visible for retry, and (b) calls `captchaService.recordCaptchaFailure(username)` so it counts toward the same lockout. Uses "was a token present in *this* request" rather than a fresh `captchaService.isRequired(...)` call — the latter would misfire on the exact request that first crosses the 3-failure threshold (before that request's own `CaptchaFilter` pass ever required a token), wrongly gating and counting an attempt that never actually went through the captcha step.

**Environment note, unrelated to the app:** this devcontainer's inner docker-in-docker daemon (for Testcontainers) failed to start in the sandbox this session ran in — kernel has no `iptables` `nat` table (`Table does not exist`, no `modprobe` available to load it). Did not affect anything above: `mysql-auth`/`mysql-resource`/`keycloak`/`mailpit` are sibling containers on the compose network, not spawned by the inner daemon, and stayed reachable throughout via Docker's embedded DNS. Not otherwise investigated or fixed — flagging in case it recurs; may be specific to that one sandbox rather than this devcontainer generally.

**Devcontainer change, same session:** `~/devbox/.devcontainer/compose.yml` now has a `claude-config` named volume on the `workspace` service (mirrors the existing `rovodev-config` pattern) mounted at `~/.claude`, so credentials/session history/memory files survive a container rebuild. It's a *new* volume — Docker only auto-populates a named volume from the image's own baked-in content on first creation, not from a currently-running container's writable layer — so this protects *future* rebuilds; it does not retroactively carry over whatever was in `~/.claude` before this volume existed.
