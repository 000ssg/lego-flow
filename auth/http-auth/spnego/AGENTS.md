# auth / http-auth / spnego — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

Implements HTTP Negotiate (SPNEGO) authentication per RFC 4559, bridging the shared GSSAPI module to HTTP authentication.

## Key Classes

- `NegotiateAuthScheme` — Implements `AuthenticationScheme`: scheme name "Negotiate", extracts SPNEGO tokens from Authorization header, processes via GSS-API, handles realm stripping from principal names
- `SpnegoConfig` — Builder-pattern config combining `GssConfig` with `stripRealmFromPrincipal` option (default: true); factory method `SpnegoConfig.of(gssConfig)`

## Tests: 29

Run: `mvn test -pl auth/http-auth/spnego -am`
