# web / http-proxy — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The **http-proxy** module provides HTTP forward and reverse proxy functionality, pluggable into the existing HTTP server/client infrastructure. It supports HTTPS tunneling (CONNECT method), load balancing, health checking, caching, authentication, and request/response filtering.

## Key Interfaces

### Forward Proxy
- **ForwardProxy** -- handles CONNECT tunneling and plain HTTP forwarding via `java.net.http.HttpClient`; default implementation opens real TCP connections to upstream servers with proper error handling (502/504)
- **ProxyAccessControl** -- interface for allow/deny rules
- **ConnectTunnel** -- bidirectional byte relay for HTTPS tunnels
- **ForwardProxyConfig** -- allowed methods, timeouts, auth requirements

### Reverse Proxy
- **ReverseProxy** -- path-based routing to backend servers
- **LoadBalancer** -- strategy interface (round-robin, least-connections)
- **HealthChecker** -- periodic backend health monitoring
- **ProxyRoute** -- route definition: path prefix to backends
- **BackendServer** -- backend definition with health/connection tracking

### Caching
- **CachingProxy** -- wraps ReverseProxy with HTTP caching
- **ProxyCacheStore** -- pluggable cache storage interface
- **InMemoryProxyCacheStore** -- LRU in-memory implementation

### Authentication
- **ProxyAuthenticator** -- interface for proxy auth (RFC 7235 section 4.3)
- **BasicProxyAuth** -- HTTP Basic proxy authentication
- **ProxyAuthHandler** -- 407 response handling

### Common
- **ProxyHandler** -- HttpRequestHandler integration for HttpRouter
- **ProxyHeaders** -- X-Forwarded-For/Proto/Host, Via, X-Real-IP utilities
- **ProxyFilter** -- request/response modification interface
- **ProxyErrorHandler** -- 502/504/503 error responses

## Package Structure

```
ssg.legoflow.http.proxy/
  forward/       -- forward proxy, CONNECT tunnel, access control
  reverse/       -- reverse proxy, load balancers, health checker, routes
  cache/         -- caching proxy, cache store, in-memory LRU
  auth/          -- proxy authentication (Basic, handler)
  demo/          -- demo forward/reverse/caching/load-balanced proxies
```

## Dependencies

- **blocks** -- core DP/DF framework
- **service** -- service lifecycle
- **http** -- HTTP protocol, HttpRequestHandler, HttpRouter, HttpServer, HttpClient

## Testing

- **Framework**: JUnit 5 + AssertJ
- **252 tests passing**
- Test classes cover: forward proxy (real HttpClient forwarding), CONNECT tunnel, access control, reverse proxy, load balancers, health checker, routes, caching, cache store, auth, demos (including DemoHttpProxyAll), headers, filters, error handling
