# HTTP Auth OAuth — RFC Compliance

## Specifications Covered

- **RFC 6749** — The OAuth 2.0 Authorization Framework
- **RFC 6750** — The OAuth 2.0 Authorization Framework: Bearer Token Usage
- **RFC 7636** — Proof Key for Code Exchange (PKCE)
- **RFC 7662** — OAuth 2.0 Token Introspection
- **RFC 7591** — OAuth 2.0 Dynamic Client Registration Protocol
- **OpenID Connect Core 1.0** — Identity layer on top of OAuth 2.0

## Compliance Matrix

### RFC 6749 — OAuth 2.0 Authorization Framework

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §4.1 | Authorization Code Grant (client-side and server-side) | ✅ Implemented | `OAuth2ClientTest`, `OAuth2AuthorizationServerTest` |
| §4.2 | Implicit Grant (response_type=token) | ✅ Implemented | `OAuth2AuthorizationServerTest` |
| §4.3 | Resource Owner Password Credentials Grant | ✅ Implemented | `OAuth2ClientTest` |
| §4.4 | Client Credentials Grant | ✅ Implemented | `OAuth2ClientTest` |
| §6 | Refreshing an Access Token | ✅ Implemented | `OAuth2ClientTest` |
| §5.1 | Successful token response: `access_token`, `token_type`, `expires_in`, `refresh_token`, `scope` | ✅ Implemented | `OAuth2TokenResponseTest` |
| §5.2 | Error response: `error`, `error_description`, `error_uri` | ✅ Implemented | `OAuth2ErrorTest` |
| §10.12 | CSRF protection via `state` parameter | ✅ Implemented | `AuthorizationRequestTest` |

### Implicit and Hybrid Flows

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| Implicit flow | `response_type=token` returns access_token in redirect fragment | ✅ Implemented | `OAuth2AuthorizationServerTest` |
| Hybrid flow (code+token) | `response_type=code token` returns both in fragment | ✅ Implemented | `OAuth2AuthorizationServerTest` |
| Hybrid flow (code+id_token) | `response_type=code id_token` returns both in fragment | ✅ Implemented | `OAuth2AuthorizationServerTest` |

### RFC 6750 — Bearer Token Usage

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §2.1 | `Authorization: Bearer <token>` request header | ✅ Implemented | `BearerAuthSchemeTest` |
| §3 | `WWW-Authenticate` response header with `realm` and `error` | ✅ Implemented | `BearerAuthSchemeTest` |
| §3 | Token validation via JWT or token store introspection | ✅ Implemented | `BearerAuthSchemeTest` |

### RFC 7636 — Proof Key for Code Exchange (PKCE)

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §4.1 | Code verifier generation (43-128 character URL-safe string) | ✅ Implemented | `PkceChallengTest` |
| §4.2 | S256 code challenge: `BASE64URL(SHA256(code_verifier))` | ✅ Implemented | `PkceChallengTest` |
| §4.3 | Plain code challenge method | ✅ Implemented | `PkceChallengTest` |
| §4.6 | Server verification of `code_verifier` against stored `code_challenge` | ✅ Implemented | `AuthorizationCodeStoreTest` |
| §4.6 | Single-use authorization codes | ✅ Implemented | `AuthorizationCodeStoreTest` |

### RFC 7662 — Token Introspection

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §2 | POST /introspect endpoint | ✅ Implemented | `OAuth2AuthorizationServerTest` |
| §2.2 | Response with `active`, `scope`, `client_id`, `sub`, `exp`, `iat`, `iss` | ✅ Implemented | `OAuth2AuthorizationServerTest` |
| §2.2 | Inactive token returns `{"active":false}` | ✅ Implemented | `OAuth2AuthorizationServerTest` |
| §2.1 | `token_type_hint` parameter support | ✅ Implemented | `OAuth2AuthorizationServerTest` |

### RFC 7591 — Dynamic Client Registration

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §2 | POST /register endpoint | ✅ Implemented | `OAuth2AuthorizationServerTest` |
| §2 | Accepts `redirect_uris`, `grant_types`, `client_name` | ✅ Implemented | `OAuth2AuthorizationServerTest` |
| §3.2 | Returns `client_id`, `client_secret`, metadata | ✅ Implemented | `OAuth2AuthorizationServerTest` |
| §3.2 | Default grant_type is `authorization_code` | ✅ Implemented | `OAuth2AuthorizationServerTest` |

### OpenID Connect Core 1.0

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §2 | ID Token with standard claims: `sub`, `iss`, `aud`, `exp`, `iat`, `nonce`, `auth_time` | ✅ Implemented | `IdTokenTest` |
| §3.1.2.1 | Authentication request with `openid` scope and `nonce` | ✅ Implemented | `OpenIdConnectClientTest` |
| §4 | Discovery metadata at `.well-known/openid-configuration` | ✅ Implemented | `OidcDiscoveryTest` |
| §5.3 | UserInfo endpoint response parsing | ✅ Implemented | `OpenIdConnectClientTest` |
| §5.3.1 | Standard UserInfo claims: `name`, `email`, `picture`, `locale`, etc. | ✅ Implemented | `UserInfoTest` |
| — | JWK Set fetching and parsing (RFC 7517) | ✅ Implemented | `JwkSetTest` |
| — | JWK Set caching with key rotation support | ✅ Implemented | `JwkSetFetcherTest` |
| — | RSA public key extraction (kty=RSA, n, e, kid) | ✅ Implemented | `JwkSetTest` |

## Test Coverage Summary
- Total compliance tests: 205
- Key test classes: `OAuth2ClientTest`, `OAuth2AuthorizationServerTest`, `TokenStoreTest`, `AuthorizationCodeStoreTest`, `OAuth2ClientRegistryTest`, `AuthorizationRequestTest`, `TokenRequestTest`, `OAuth2TokenResponseTest`, `OAuth2ErrorTest`, `PkceChallengTest`, `BearerAuthSchemeTest`, `OpenIdConnectClientTest`, `OidcDiscoveryTest`, `IdTokenTest`, `UserInfoTest`, `OAuthProviderTest`, `JwkSetTest`, `JwkSetFetcherTest`
