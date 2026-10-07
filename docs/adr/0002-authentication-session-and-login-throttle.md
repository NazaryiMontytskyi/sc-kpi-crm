# ADR-0002: Session authentication and in-memory login throttling

- Status: Accepted
- Date: 2026-10-07

## Context
INC-004 needs sign-in, brute-force protection and a CSRF-safe SPA contract (CONTEXT.md section 6). Scale: ~80 users, one backend instance.

## Decision
- Server-side HTTP session in an HttpOnly cookie (`CRMSESSION`, SameSite=Lax, Secure in prod, 8 h idle timeout); CSRF via `XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header. BCrypt (cost 12) hashes; each user has an individual password.
- Authentication goes through a `ProviderManager` of all `AuthenticationProvider` beans, so an OIDC provider (KPI ID, [LATER]) can be added without changes.
- Every request re-reads the account (PK lookup): blocked/archived users lose open sessions at once, and `mustChangePassword` (403 `PASSWORD_CHANGE_REQUIRED`) and locale are always current.
- **Throttle state is in memory** (`LoginThrottle`, key = login+IP, 5 failures / 15 min -> 15 min lockout, doubling, capped at 24 h). Reasons: single instance, no schema or cleanup job, simplest thing that works. Consequences: a restart clears lockouts; with several instances limits would be per instance; an attacker rotating IPs against one login is not stopped by this layer (a per-login limiter or a DB-backed store can replace it behind the same class if needed). Unknown logins are throttled identically, so lockouts leak nothing.
- Behind a reverse proxy the client IP must be the real one (configure Tomcat remote-ip / forwarded headers in INC-049), otherwise all users share one IP key.
- Audit actor/auditor come from the security context (`SecurityContextActor` is `@Primary` over the audit fallback; the identity module provides `auditorAware`, replacing the kernel placeholder).
- OpenAPI/Swagger UI: public only with `crm.identity.public-api-docs=true` (dev, test); otherwise authenticated; springdoc is disabled in prod.
