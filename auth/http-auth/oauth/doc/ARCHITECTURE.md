# HTTP Auth OAuth — Architecture

## OAuth 2.0 Client Flow

```mermaid
graph TD
    A["1. OAuth2Client.startAuthorizationCodeFlow()"] --> B["AuthorizationRequest → buildUrl()<br/>→ redirect user to authorization endpoint"]
    B --> C["2. User authorizes<br/>→ redirect back with code + state"]
    C --> D["3. OAuth2Client.exchangeAuthorizationCode()<br/>→ TokenRequest → POST to token endpoint"]
    D --> E["4. OAuth2Client.parseTokenResponse()<br/>→ OAuth2TokenResponse<br/>(access_token, refresh_token, expires_in)"]
```

## OAuth 2.0 Server Flow

```mermaid
graph TD
    A["1. handleAuthorize(request)"] --> B["Validate client, redirect_uri, scopes"]
    B --> C["Issue authorization code<br/>via AuthorizationCodeStore"]
    C --> D["Redirect with code + state"]
    E["2. handleToken(request)"] --> F["Validate grant_type, client authentication"]
    F --> G["Exchange code for tokens<br/>(verify PKCE if present)"]
    G --> H["Issue access_token + refresh_token<br/>via TokenStore"]
```

## Bearer Authentication

BearerAuthScheme supports two validation strategies:
1. JWT validation via JwtTokenProvider (signature + expiry + issuer)
2. Token store introspection via TokenStore
3. Fallback: try JWT first, fall back to token store

## OIDC Layer

OpenIdConnectClient wraps OAuth2Client adding:
- Nonce generation for ID token binding
- ID token parsing (JWT without verification) and validation
- UserInfo response parsing
- Discovery URL construction

## Provider Architecture

OAuthProvider (abstract) provides template with:
- authorizationEndpoint(), tokenEndpoint(), userInfoEndpoint(), revocationEndpoint()
- defaultScopes()
- buildConfig() factory method

Concrete providers: GoogleOAuth, GitHubOAuth, MicrosoftOAuth (configurable tenant), FacebookOAuth, TwitterOAuth, AppleOAuth, GenericOAuth.

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../../../doc/ARCHITECTURE.md) | [Root README](../../../../README.md)
