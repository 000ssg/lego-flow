# Web Services Module — Requirements Evolution

## Module Timeline Overview

- **Start Date**: June 16, 2026
- **Total Tests**: 81
- **Purpose**: Web service components for HTTP (endpoints, routing, content negotiation, JSON/XML codecs)

---

## Table of Contents

- [Module Timeline Overview](#module-timeline-overview)
- **Commits by date (newest → oldest)**
  - [Async Wrapper + DemoWebServicesAll](#commit-async-wrapper--demowebservicesall-2026-07-07)
  - [Initial Commit — Web Service Components](#initial-commit--web-service-components-2026-06-16)

---

## Initial Commit — Web Service Components (2026-06-16)

### Original Request
> "Implement web service components for HTTP — provides web service abstractions (endpoints, routing, content negotiation, JSON/XML codecs) that plug into the HTTP module."

### Reformulated Requirements
1. Define `WebService` as the top-level web service abstraction with endpoints and codecs
2. Provide `WebServiceRegistry` for service discovery and lookup
3. Implement `WebServiceFilter` for chainable cross-cutting concerns
4. Define `WebServiceDescriptor` for service metadata
5. Extend service context with web-specific attributes via `WebServiceContext`
6. Implement `Endpoint` and `EndpointInvoker` for HTTP method + path routing
7. Provide `RequestMapper` and `ResponseMapper` for HTTP <-> domain object transformation
8. Implement `JsonCodec` and `XmlCodec` for pluggable content serialization
9. Support content negotiation based on Accept/Content-Type headers
10. Maintain dual API: sync + async, procedural + functional

### Final Design Decisions
- **DP/DF-based web services** — web services built on the blocks DataProcessor/DataFilter foundation, consistent with the framework's layered architecture
- **Pluggable content codecs** — JsonCodec and XmlCodec as independent, swappable components; new codecs can be added without modifying core service logic
- **Filter-based routing** — WebServiceFilter chain processes requests before endpoint dispatch, enabling auth, logging, CORS, and rate limiting as composable filters
- **Registry pattern** — centralized WebServiceRegistry for service discovery, supporting both name-based and descriptor-based lookup

### Implementation Details
- `WebService` — top-level service abstraction with builder API
- `WebServiceRegistry` / `DefaultWebServiceRegistry` — service registration and lookup
- `WebServiceFilter` — chainable request/response filter
- `WebServiceDescriptor` — service metadata (name, version, endpoints, content types)
- `WebServiceContext` — web-aware context with HTTP method, path, headers, query params
- `Endpoint` / `EndpointInvoker` — route mapping and execution
- `RequestMapper` / `ResponseMapper` — HTTP <-> domain object transformation
- `JsonCodec` — JSON serialization/deserialization
- `XmlCodec` — XML serialization/deserialization
- Demo services: hello world, todo API

### Test Coverage
- 19 tests passing
- Unit tests for codecs, mappers, endpoints, filters, registry
- Functional demo tests for hello world and todo API services

---

## Commit: Demo Expansion — Remaining Sources and Tests (2026-06-16)

### Original Request
> "Single commit adding remaining demo sources and tests across all 4 modules. Web Services: 4 new sources + 7 new tests, test count up from 19 to 69. Sources: ContentNegotiator, UserApiService, MultiFormatService, CompositeApiServer. Tests: WebServiceTest, ContentNegotiatorTest, EchoDemoTest, UserApiDemoTest, ContentNegotiationDemoTest, MultiEndpointDemoTest, WebServiceFilterDemoTest."

### Reformulated Requirements

1. **`ContentNegotiator`** — dedicated content negotiation component that selects the best codec from a service's registered codec list based on the incoming `Accept` and `Content-Type` headers; decouples negotiation logic from `WebService` for testability and reuse
2. **`UserApiService`** — realistic CRUD API demo service with user domain objects, multiple endpoints (GET list, GET by id, POST, DELETE), and both JSON and XML codec support
3. **`MultiFormatService`** — demo service that simultaneously serves responses in JSON, XML, and plain text based on the client's `Accept` header, exercising all three codec paths in a single service
4. **`CompositeApiServer`** — composite server demo assembling multiple `WebService` instances (hello, todo, user, multi-format) behind a single `WebServiceRegistry`, demonstrating registry-based routing at scale
5. **Unit tests** — `WebServiceTest` (core API), `ContentNegotiatorTest` (negotiation algorithm) covering exact match, q-factor ordering, wildcard fallback, and 406 scenarios
6. **Demo functional tests** — `EchoDemoTest`, `UserApiDemoTest`, `ContentNegotiationDemoTest`, `MultiEndpointDemoTest`, `WebServiceFilterDemoTest` exercising the new demo classes end-to-end

### Final Design Decisions

- **`ContentNegotiator` as standalone class** — the content negotiation algorithm is complex enough (q-factor parsing, specificity tiebreaking, wildcard handling) to warrant its own class and test rather than being buried in `WebService` internals; this is the architectural change for this commit
- **`CompositeApiServer`** uses `DefaultWebServiceRegistry` with multiple registrations — validates that the registry scales to multi-service deployment without modification
- `MultiFormatService` explicitly registers `JsonCodec`, `XmlCodec`, and a plain-text codec — serves as the definitive integration test for the codec selection path

### Implementation Details

- **4 new source files**: `ContentNegotiator.java` (in `content/` sub-package), `UserApiService.java`, `MultiFormatService.java`, `CompositeApiServer.java` (in `demo/` sub-packages)
- **7 new test files**: 2 unit tests + 5 demo functional tests
- `ContentNegotiator` added to `content/` alongside existing `JsonCodec` and `XmlCodec`

### Test Coverage

- **New unit tests**: `WebServiceTest`, `ContentNegotiatorTest`
- **New demo tests**: `EchoDemoTest`, `UserApiDemoTest`, `ContentNegotiationDemoTest`, `MultiEndpointDemoTest`, `WebServiceFilterDemoTest`
- **Total: 69 Web Services tests (764 total project)**

---

## Commit: Async Wrapper + DemoWebServicesAll (2026-07-07)

### Original Request
> "Implement Web-Services Async Wrapper + DemoWebServicesAll. Create async wrappers for the main web-services API following the same pattern as service/AsyncService.java. The async wrapper should wrap the main web-service dispatch/handling classes, return CompletableFuture<T> for each operation, use Executors.newVirtualThreadPerTaskExecutor() internally, and document the sync-primary design rationale. Also create DemoWebServicesAll following the DemoAll pattern."

### Reformulated Requirements
1. Create `AsyncWebService` wrapping `WebService` with `CompletableFuture<T>` return types
2. Create `AsyncWebServiceRegistry` wrapping `WebServiceRegistry` with async operations
3. Create `AsyncEndpointInvoker` wrapping `EndpointInvoker` with async invocation
4. All async wrappers use `Executors.newVirtualThreadPerTaskExecutor()` and support custom executors
5. All async wrappers expose a `sync()` method for direct access to the delegate
6. Document sync-primary design rationale in Javadoc
7. Create `DemoWebServicesAll` covering all 7 feature areas: content negotiation, route dispatch, REST endpoints, async dispatch, error handling, filter chain, response formats
8. Create `DemoWebServicesAllTest` verifying all feature sections
9. Create `AsyncWebServiceTest` unit tests for all 3 async wrappers
10. Update COMPLIANCE.md, ARCHITECTURE.md, README.md, CLAUDE.md

### Final Design Decisions
- **Three async wrappers** — `AsyncWebService`, `AsyncWebServiceRegistry`, `AsyncEndpointInvoker` cover the main API surface (service handling, discovery, endpoint invocation)
- **Sync-primary pattern** — consistent with `service/AsyncService`: virtual threads make blocking calls near-zero-cost; async exists for `CompletableFuture` composability
- **Custom executor support** — all wrappers accept optional `ExecutorService` for testing and alternative threading models
- **DemoWebServicesAll pattern** — follows `DemoHttpAll` structure with `USE_EXTERNAL` flag, `Results` record, and per-feature demo methods

### Implementation Details
- **3 new async wrapper sources**: `AsyncWebService.java`, `AsyncWebServiceRegistry.java`, `AsyncEndpointInvoker.java`
- **1 new demo source**: `DemoWebServicesAll.java` with 7 demo sections
- **2 new test files**: `AsyncWebServiceTest.java` (11 tests), `DemoWebServicesAllTest.java` (1 test with 7 assertions)
- Async wrappers follow the established pattern: hold sync delegate, create virtual thread executor, wrap calls in `CompletableFuture.supplyAsync()`/`runAsync()`

### Test Coverage
- **New tests**: 12 (11 async wrapper unit tests + 1 DemoAll integration test)
- **Total: 81 Web Services tests**

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~50k |
| Agent tool calls | ~30 |
| Agent wall time | ~15 min |
| Files created/modified | 11 (5 new sources, 2 new tests, 4 doc updates) |
| Lines added/removed | +700 / -3 |
| Tests added | 12 (total: 81) |
