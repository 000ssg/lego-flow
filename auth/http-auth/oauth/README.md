# Lego Flow HTTP Auth OAuth

OAuth 2.0, OpenID Connect, Bearer tokens, and pre-configured OAuth providers for the Lego Flow framework.

## Features

- OAuth 2.0 Authorization Code flow
- OAuth 2.0 Client Credentials grant
- OAuth 2.0 Resource Owner Password grant
- OAuth 2.0 Refresh Token grant
- PKCE extension (RFC 7636) with S256
- Bearer token authentication (RFC 6750)
- Complete OAuth 2.0 authorization server implementation
- OpenID Connect: discovery, ID token, UserInfo
- Pre-configured providers: Google, GitHub, Microsoft, Facebook, Twitter, Apple
- Generic configurable provider for any OAuth 2.0 server

## Usage

```java
// OAuth 2.0 client with PKCE
var config = OAuth2Config.builder()
    .clientId("client-id")
    .clientSecret("secret")
    .redirectUri("http://localhost/callback")
    .authorizationEndpoint("https://auth.example.com/authorize")
    .tokenEndpoint("https://auth.example.com/token")
    .scopes(Set.of("openid", "email"))
    .build();
var client = new OAuth2Client(config);
var authRequest = client.startAuthorizationCodeFlowWithPkce();
String authUrl = authRequest.buildUrl();

// Pre-configured Google
var googleConfig = GoogleOAuth.INSTANCE.buildConfig("client-id", "secret", "http://localhost/cb");

// OIDC
var oidc = new OpenIdConnectClient(client, jwtProvider);
var idToken = oidc.parseIdToken(rawToken);
```

## Tests: 175

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [HTTP Auth README](../README.md) | [Auth Module README](../../README.md) | [Root README](../../../README.md)
