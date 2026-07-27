# Web Services Module — Development Guide

## Module Overview

The **web-services** module provides web service components for HTTP. It defines abstractions for building web services: `WebService`, `WebServiceRegistry`, `WebServiceFilter`, `WebServiceDescriptor`, `WebServiceContext`, `Endpoint`, `EndpointInvoker`, `RequestMapper`, `ResponseMapper`, `JsonCodec`, and `XmlCodec`. These components plug into the HTTP module to enable endpoint routing, content negotiation, and request/response mapping.

## Key Interfaces

### WebService
Top-level web service abstraction — defines a named service with endpoints and content codecs.

### WebServiceRegistry
Registry for discovering, registering, and looking up web services by name or descriptor.

### AsyncWebService
Async wrapper returning `CompletableFuture<T>`. Delegates to sync `WebService` on virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`.

### AsyncWebServiceRegistry
Async wrapper for `WebServiceRegistry` returning `CompletableFuture<T>`.

### WebServiceFilter
Filter applied to web service request/response processing pipeline. Supports chaining for cross-cutting concerns (auth, logging, CORS).

### WebServiceDescriptor
Metadata describing a web service: name, version, endpoints, supported content types.

### WebServiceContext
Extends service context with web-specific attributes: HTTP method, path, headers, query parameters.

### Endpoint
Route target mapping an HTTP method + path pattern to an `EndpointInvoker`.

### EndpointInvoker
Executes endpoint logic given a mapped request, producing a response.

### RequestMapper / ResponseMapper
Transform HTTP requests into domain objects and domain results into HTTP responses. Pluggable per content type.

### JsonCodec / XmlCodec
Content codecs for JSON and XML serialization/deserialization. Used by request/response mappers for content negotiation.

## Dependencies
- blocks (core DP/DF framework)
- service (service lifecycle, scoped contexts, dual API)
- http (HTTP protocol, request/response model)

### AsyncEndpointInvoker
Async wrapper for `EndpointInvoker` returning `CompletableFuture<HttpResponse>`.

## Dual API Convention

- **Sync procedural** — `webService.handle(ctx, request)`, `registry.getService(path)`
- **Async wrapper** — `AsyncWebService`, `AsyncWebServiceRegistry`, `AsyncEndpointInvoker` return `CompletableFuture<T>`, delegate to sync on virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`
- **Functional style** — lambda-friendly endpoint builders, composable filter chains

## Testing

- **JUnit 5**: test framework
- **AssertJ**: fluent assertions
- **81 tests passing**

## Commit Rules
- Update doc/REQUIREMENTS.md with commit section
- Update doc/ARCHITECTURE.md if architecture changed
- Update README.md for API changes
- Include `Co-Authored-By: AI assistant` in all commits
