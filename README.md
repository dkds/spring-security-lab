# Spring Security Lab

A hands-on reference lab exploring authentication and authorization patterns with
**Spring Security** and the **Spring Authorization Server**, across both Spring
Boot 3 and Spring Boot 4. Each scenario is a self-contained set of services that
implements one flow end to end, and has its own README with module details, run
instructions, and its flow diagram.

> Reference implementations for learning and demonstration - not hardened for production use.

---

## Scenarios

| Scenario                                           | Focus |
|----------------------------------------------------|---|
| [`oauth2-spring-boot-3`](./oauth2-spring-boot-3)   | OAuth 2.1 authorization-code flow with a customizable login page (Spring Boot 3) |
| [`saml2-spring-boot-3`](./saml2-spring-boot-3)     | SAML2 single sign-on with Keycloak (Spring Boot 3) |
| [`spring-boot-4-minimal`](./spring-boot-4-minimal) | The same authorization-code flow on Spring Boot 4, with a resource server |

---

## Concepts covered

- Spring Authorization Server on both Spring Boot 3 and Spring Boot 4
- OAuth 2.1 authorization-code grant with PKCE
- Customizable login page (migrating away from a custom password-grant flow)
- Access-token validation at a resource server
- SAML2 SP-initiated SSO with Keycloak as the identity provider
- Clear separation of authorization server, resource server, and client concerns

---

## Tech at a glance

- **Backends:** Java 24 / 25, Maven, Spring Boot 3 and 4, Spring Security, Spring Authorization Server
- **Frontends:** React and Next.js with TypeScript (one module in JavaScript), Vite, pnpm
- **Data & infra:** MySQL and Keycloak via Docker Compose; embedded H2 in the Spring Boot 4 auth service

See each scenario's README for module-level stacks, run instructions, and flow diagrams.
