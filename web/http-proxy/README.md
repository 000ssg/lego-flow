# Lego Flow HTTP Proxy

![Version](https://img.shields.io/badge/version-0.1.0-SNAPSHOT-blue)
![JDK](https://img.shields.io/badge/JDK-25-orange)
![Tests](https://img.shields.io/badge/tests-252%20passing-brightgreen)
![License](https://img.shields.io/badge/license-proprietary-lightgrey)

HTTP forward and reverse proxy module for the Lego Flow framework. Plugs into the existing HTTP server/client infrastructure via `HttpRequestHandler` and `HttpRouter`.

## Features

### Forward Proxy
- Plain HTTP request forwarding with URI rewriting
- HTTPS tunneling via CONNECT method with bidirectional byte relay
- Configurable access control (allowlist/denylist)
- Via header per RFC 7230 section 5.7.1
- X-Forwarded-For/Proto/Host support
- Pluggable request/response filters

### Reverse Proxy
- Path-based routing to backend servers
- Load balancing: round-robin (weighted) and least-connections
- Periodic backend health checking
- Host header rewriting with X-Forwarded-* headers
- Path prefix stripping
- WebSocket upgrade forwarding
- Query string preservation

### Caching Proxy
- Cache-Control, Expires, ETag, Last-Modified support
- Conditional requests (If-None-Match, If-Modified-Since)
- Cache invalidation on PUT/POST/DELETE
- In-memory LRU cache with size/entry limits
- Pluggable cache storage interface
- Configurable included/excluded paths

### Authentication
- HTTP Basic proxy authentication (RFC 7235 section 4.3-4.4)
- Proxy-Authorization / Proxy-Authenticate headers
- 407 Proxy Authentication Required handling

## Quick Start

### Forward Proxy
```java
var config = new ForwardProxyConfig();
config.setProxyName("my-proxy");
var accessControl = ProxyAccessControl.allowHosts(Set.of("example.com"));
var proxy = new ForwardProxy(config, accessControl);
HttpResponse response = proxy.handleRequest(request);
```

### Reverse Proxy
```java
var config = new ReverseProxyConfig();
var proxy = new ReverseProxy(config);
proxy.addRoute(new ProxyRoute("/api",
    List.of(new BackendServer("backend1", 8081), new BackendServer("backend2", 8082)),
    new RoundRobinBalancer(), true));
HttpResponse response = proxy.handleRequest(request);
```

### Caching Proxy
```java
var cachingProxy = new CachingProxy(reverseProxy,
    new InMemoryProxyCacheStore(10000, 64 * 1024 * 1024),
    new ProxyCacheConfig());
HttpResponse response = cachingProxy.handleRequest(request);
```

### Integration with HttpRouter
```java
var handler = ProxyHandler.forReverseProxy(reverseProxy);
router.route("/api", HttpMethod.GET, handler);
```

## Module Dependencies

- `lego-flow-blocks` -- core DP/DF framework
- `lego-flow-service` -- service lifecycle
- `lego-flow-http` -- HTTP protocol implementation

## Test Coverage

252 tests across 18 test classes covering forward proxy (with real `java.net.http.HttpClient` forwarding), reverse proxy, load balancing, health checking, caching, authentication, headers, filters, error handling, CONNECT tunneling, and integration demos including comprehensive `DemoHttpProxyAll`.

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
