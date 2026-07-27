# HTTP Proxy Module -- Requirements

## Initial Implementation

### Original Request
> Create a new `http-proxy` module implementing HTTP proxy functionality -- both forward proxy and reverse proxy. This should be pluggable into the existing HTTP server/client infrastructure.

### Reformulated Requirements

1. **Forward Proxy**: Handle CONNECT method for HTTPS tunneling, plain HTTP request forwarding with URI rewriting, configurable access control, connection pooling, Via and X-Forwarded-For headers
2. **Reverse Proxy**: Path-based routing to backends, load balancing (round-robin, least-connections), health checking, header rewriting, path prefix stripping, WebSocket proxy support, streaming body forwarding
3. **Caching Proxy**: Honor Cache-Control/Expires/ETag/Last-Modified, pluggable cache storage, cache invalidation on PUT/POST/DELETE, conditional request forwarding
4. **Authentication**: Proxy-Authorization/Proxy-Authenticate per RFC 7235 section 4.3-4.4, HTTP Basic authentication
5. **Integration**: HttpRequestHandler integration for HttpRouter, ProxyFilter for request/response modification, ProxyErrorHandler for 502/504/503
6. **Testing**: 150+ tests covering all components

### Final Design Decisions

- Forward proxy and reverse proxy are separate classes (`ForwardProxy`, `ReverseProxy`) with distinct configurations
- `ProxyHandler` bridges to `HttpRequestHandler` for integration with `HttpRouter`
- Load balancing is strategy-based via `LoadBalancer` interface
- Caching wraps `ReverseProxy` as a decorator (`CachingProxy`)
- Cache storage is pluggable via `ProxyCacheStore` interface with `InMemoryProxyCacheStore` default
- Access control for forward proxy is pluggable via `ProxyAccessControl` interface
- Virtual threads used for CONNECT tunnel relay
- `ProxyHeaders` utility class for standard proxy header manipulation

### Implementation Details

#### Files Created
- **Common**: ProxyHandler, ProxyHeaders, ProxyFilter, ProxyErrorHandler
- **Forward**: ForwardProxy, ForwardProxyConfig, ProxyAccessControl, HostBasedAccessControl, ConnectTunnel
- **Reverse**: ReverseProxy, ReverseProxyConfig, BackendServer, LoadBalancer, RoundRobinBalancer, LeastConnectionsBalancer, HealthChecker, ProxyRoute
- **Cache**: CachingProxy, ProxyCacheConfig, ProxyCacheStore, InMemoryProxyCacheStore
- **Auth**: ProxyAuthenticator, BasicProxyAuth, ProxyAuthHandler
- **Demos**: SimpleForwardProxyDemo, ReverseProxyDemo, CachingProxyDemo, LoadBalancedProxyDemo

### Test Coverage

- 251 tests passing across 17 test classes
- Forward proxy: 15 tests (plain HTTP, CONNECT, Via, XFF, access control, auth, filters)
- CONNECT tunnel: 9 tests (creation, bidirectional relay, counters, close)
- Access control: 14 tests (allow/deny lists, case insensitivity, empty lists)
- Reverse proxy: 15 tests (routing, path stripping, host rewriting, WebSocket, filters)
- Load balancer: 13 tests (round-robin, weighted, least-connections, unhealthy)
- Health checker: 12 tests (add/remove, healthy/unhealthy, start/stop, exceptions)
- Proxy route: 18 tests (matching, rewriting, factories, strip prefix)
- Backend server: 14 tests (creation, health, connections, equality)
- Caching proxy: 15 tests (hits/misses, invalidation, conditional, no-cache, no-store)
- Cache store: 20 tests (LRU eviction, size limits, hit ratio, expiry)
- Auth: 13 + 10 tests (Basic auth, challenge, handler)
- Headers: 14 tests (XFF, Via, proto, host, real-ip)
- Filters: 10 tests (identity, chaining, ordering)
- Error handler: 12 tests (502/504/503, exception mapping)
- Proxy handler: 8 tests (forward/reverse integration)
- Demo tests: 10 + 10 + 9 + 10 tests

---

## Commit: `pending` - HTTP Forward Proxy Real Forwarding + DemoHttpProxyAll (2026-07-07)

### Original Request
> Implement HTTP Forward Proxy real forwarding using java.net.http.HttpClient instead of returning 502. Create DemoHttpProxyAll comprehensive demo covering all proxy module features. Add DemoHttpProxyAllTest.

### Reformulated Requirements

1. **ForwardProxy real forwarding**: Replace the 502-returning `simulateUpstreamRequest()` default with a real `java.net.http.HttpClient` implementation that opens connections to upstream servers, forwards requests, and maps responses back
2. **Error handling**: `ConnectException` produces 502, `HttpTimeoutException`/`SocketTimeoutException` produces 504, `IOException` produces 502, `InterruptedException` produces 502
3. **Hop-by-hop header filtering**: Strip restricted and hop-by-hop headers when forwarding to/from upstream
4. **DemoHttpProxyAll**: Comprehensive demo class with `Results` record and `runAll()` method covering 7 feature areas
5. **DemoHttpProxyAllTest**: Test class verifying all 7 feature areas pass
6. **Backward compatibility**: All existing tests (which override `simulateUpstreamRequest()`) must continue to pass

### Final Design Decisions

- Real forwarding uses `java.net.http.HttpClient` (JDK built-in, no external dependencies)
- `simulateUpstreamRequest()` remains `protected` and overridable for testing
- Hop-by-hop headers managed via `RESTRICTED_HEADERS` and `RESPONSE_HOP_BY_HOP` constant sets
- `HttpClient` instances are created per request with `try-with-resources` for clean lifecycle
- Follow redirects disabled (`Redirect.NEVER`) since the proxy should not follow redirects on behalf of the client
- DemoHttpProxyAll covers: forward proxy, reverse proxy, filters, caching, CONNECT tunnel, proxy headers, error handling

### Implementation Details

#### Files Modified
- `web/http-proxy/src/main/java/ssg/legoflow/http/proxy/forward/ForwardProxy.java` -- replaced stub `simulateUpstreamRequest()` with real `java.net.http.HttpClient` forwarding, added `mapUpstreamResponse()`, `RESTRICTED_HEADERS`, `RESPONSE_HOP_BY_HOP`

#### Files Created
- `web/http-proxy/src/main/java/ssg/legoflow/http/proxy/demo/DemoHttpProxyAll.java` -- comprehensive demo with 7 feature sections
- `web/http-proxy/src/test/java/ssg/legoflow/http/proxy/demo/DemoHttpProxyAllTest.java` -- functional test

### Test Coverage

- 1 new test class: DemoHttpProxyAllTest (1 test verifying 7 feature areas)
- Total tests: 252 passing across 18 test classes

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~50000 |
| Agent tool calls | ~30 |
| Agent wall time | ~15 min |
| Files created/modified | 3 |
| Lines added/removed | +400 / -5 |
| Tests added | 1 (total: 252) |
