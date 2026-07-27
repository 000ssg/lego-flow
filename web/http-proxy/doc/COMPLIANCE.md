# HTTP Proxy Module — RFC Compliance

## Specifications Covered

- **RFC 7230** — HTTP/1.1 Message Syntax and Routing (§5.7 Message Forwarding)
- **RFC 7231** — HTTP/1.1 Semantics and Content (§4.3.6 CONNECT Method)
- **RFC 7232** — HTTP/1.1 Conditional Requests (ETags, Last-Modified)
- **RFC 7234** — HTTP/1.1 Caching (Cache-Control directives)
- **RFC 7235** — HTTP/1.1 Authentication (§4.3–4.4 Proxy Auth)
- **RFC 7617** — HTTP Basic Authentication (Base64 credentials)

## Compliance Matrix

### RFC 7230 — Message Forwarding

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §5.7.1 | Via header appended in `<protocol-version> <pseudonym>` format | ✅ Implemented | `ProxyHeadersTest` |
| §5.7.1 | Via header added by ForwardProxy when `addViaHeader` config is true | ✅ Implemented | `ForwardProxyTest` |
| §5.7.1 | Via header added by ReverseProxy when `addViaHeader` config is true | ✅ Implemented | `ReverseProxyTest` |
| §5.7.1 | Multiple proxy hops accumulate comma-separated Via entries | ✅ Implemented | `ProxyHeadersTest` |
| §5.7.1 | Via header applied to both request and response | ✅ Implemented | `ProxyHeadersTest` |
| §5.7 | `Proxy-Authorization` stripped before forwarding (hop-by-hop) | ✅ Implemented | `ProxyHeadersTest` |
| §5.7 | `Proxy-Connection` stripped before forwarding (hop-by-hop) | ✅ Implemented | `ProxyHeadersTest` |
| §5.7 | `TE` stripped before forwarding (hop-by-hop) | ✅ Implemented | `ProxyHeadersTest` |
| §5.7 | `Trailer` stripped before forwarding (hop-by-hop) | ✅ Implemented | `ProxyHeadersTest` |
| §5.7 | `Transfer-Encoding` stripped by forward proxy (hop-by-hop) | ✅ Implemented | `ForwardProxyTest` |
| §5.7 | `Upgrade` stripped by forward proxy (hop-by-hop) | ✅ Implemented | `ForwardProxyTest` |

### De Facto Forwarding Headers (X-Forwarded-*)

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| De facto | `X-Forwarded-For`: client IP chain appended on each hop | ✅ Implemented | `ProxyHeadersTest` |
| De facto | `X-Forwarded-Proto`: original protocol (http/https) | ✅ Implemented | `ProxyHeadersTest` |
| De facto | `X-Forwarded-Host`: original Host header value | ✅ Implemented | `ProxyHeadersTest` |
| De facto | `X-Real-IP`: original client IP | ✅ Implemented | `ProxyHeadersTest` |

### RFC 7235 — Proxy Authentication

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §4.3 | `Proxy-Authenticate` header in 407 response with `Basic realm="..."` | ✅ Implemented | `ProxyAuthHandlerTest` |
| §4.4 | `Proxy-Authorization: Basic <credentials>` header validation | ✅ Implemented | `BasicProxyAuthTest` |
| §4.4 | Base64-encoded `username:password` decoding per RFC 7617 | ✅ Implemented | `BasicProxyAuthTest` |
| §4.4 | Passwords containing colons are supported | ✅ Implemented | `BasicProxyAuthTest` |
| §4.3–4.4 | Full 407 challenge/response authentication flow | ✅ Implemented | `ProxyAuthHandlerTest` |

### RFC 7234 — HTTP Caching

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §5.2.1.4 | `no-cache` directive: bypasses cache, forwards to upstream | ✅ Implemented | `CachingProxyTest` |
| §5.2.1.5 | `no-store` directive: response not cached | ✅ Implemented | `CachingProxyTest` |
| §5.2.1.1 | `max-age=N` directive: sets cache TTL to N seconds | ✅ Implemented | `CachingProxyTest` |
| §5.2.2.6 | `private` directive: not cached unless `cachePrivate` config is true | ✅ Implemented | `CachingProxyTest` |
| §4 | `POST`, `PUT`, `DELETE`, `PATCH` invalidate corresponding GET cache entry | ✅ Implemented | `CachingProxyTest` |

### RFC 7232 — Conditional Requests

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §2.3 | `ETag` / `If-None-Match` entity tag comparison for cache validation | ✅ Implemented | `CachingProxyTest` |
| §2.2 | `Last-Modified` / `If-Modified-Since` timestamp comparison | ✅ Implemented | `CachingProxyTest` |
| §4.1 | Returns `304 Not Modified` when conditions match | ✅ Implemented | `CachingProxyTest` |

### RFC 7231 — CONNECT Method

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §4.3.6 | Forward proxy handles CONNECT for HTTPS tunneling | ✅ Implemented | `ConnectTunnelTest` |
| §4.3.6 | Responds with `200 Connection Established` | ✅ Implemented | `ConnectTunnelTest` |
| §4.3.6 | Bidirectional byte relay via virtual threads (`ConnectTunnel`) | ✅ Implemented | `ConnectTunnelTest` |
| §4.3.6 | Tunnel lifetime bounded by configurable idle timeout | ✅ Implemented | `ConnectTunnelTest` |

## Test Coverage Summary
- Total compliance tests: 251
- Key test classes: `ProxyHeadersTest`, `ForwardProxyTest`, `ReverseProxyTest`, `ConnectTunnelTest`, `BasicProxyAuthTest`, `ProxyAuthHandlerTest`, `CachingProxyTest`, `ProxyCacheStoreTest`, `LoadBalancerTest`, `HealthCheckerTest`, `ProxyFilterTest`, `ProxyErrorHandlerTest`, `ProxyAccessControlTest`, `ProxyDemoTest`
