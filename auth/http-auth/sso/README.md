# Lego Flow HTTP Auth SSO

Single Sign-On federation, reverse proxy SSO, and SAML 2.0 assertion parsing for the Lego Flow framework.

## Features

- JWT-based SSO with federated sessions across services
- Cookie-based session tracking with configurable domain and security
- Federated logout with service propagation
- Reverse proxy SSO with header injection (x-forwarded-user, x-forwarded-roles, etc.)
- Header stripping to prevent spoofing
- SAML 2.0 assertion parsing (NameID, Issuer, Attributes, Conditions)
- Base64-encoded SAML response support
- Principal conversion from SAML assertions

## Usage

```java
// SSO Manager
var config = SsoConfig.forDomain("example.com");
var jwt = JwtTokenProvider.hmac256("secret-key-32-bytes!!!!!!!!!!!!!!", "sso", Duration.ofHours(8));
var ssoManager = new SsoManager(config, jwt);

// Login
var session = ssoManager.login(principal, response);
session.addAuthenticatedService("https://app1.example.com");

// Validate
var validSession = ssoManager.validateSession(request);

// Reverse Proxy SSO
var proxyConfig = ReverseProxySsoConfig.defaults();
var proxy = new ReverseProxySso(proxyConfig);
var principal = proxy.extractPrincipal(request);

// SAML
var samlConfig = SamlConfig.of("https://idp.example.com", "https://idp.example.com/sso");
var parser = new SamlAssertionParser(samlConfig);
var assertion = parser.parseResponse(samlXml);
```

## Tests: 103

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [HTTP Auth README](../README.md) | [Auth Module README](../../README.md) | [Root README](../../../README.md)
