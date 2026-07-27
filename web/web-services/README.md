
# Lego Flow Web Services — Web Service Components for HTTP

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Build-Maven-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()
[![Tests](https://img.shields.io/badge/Tests-81_passing-brightgreen.svg)]()

Web service components that plug into the HTTP module, providing endpoint routing, content negotiation, and request/response mapping with JSON and XML codecs.

## Features

- **Web Service Registry** — discover, register, and look up services by name or descriptor
- **Endpoint Routing** — map HTTP method + path patterns to endpoint invokers
- **Content Negotiation** — `ContentNegotiator` with q-factor parsing, specificity tiebreaking, and wildcard handling; selects best codec from Accept/Content-Type headers
- **JSON/XML Codecs** — pluggable serialization/deserialization for JSON and XML
- **Request/Response Mapping** — transform HTTP messages to/from domain objects
- **Filters** — chainable cross-cutting concerns (auth, logging, CORS, rate limiting)
- **Async Wrappers** — `AsyncWebService`, `AsyncWebServiceRegistry`, `AsyncEndpointInvoker` returning `CompletableFuture<T>` via virtual threads
- **Dual API** — sync + async, procedural + functional styles

## Quick Start

### Hello World Service

```java
var service = WebService.builder("hello")
    .endpoint(Endpoint.get("/hello", ctx -> "Hello, World!"))
    .codec(new JsonCodec())
    .build();

var registry = new DefaultWebServiceRegistry();
registry.register(service);
```

### Todo API

```java
var todoService = WebService.builder("todos")
    .endpoint(Endpoint.get("/todos", ctx -> todoStore.findAll()))
    .endpoint(Endpoint.post("/todos", ctx -> {
        var todo = ctx.requestBody(Todo.class);
        return todoStore.save(todo);
    }))
    .endpoint(Endpoint.get("/todos/{id}", ctx -> {
        var id = ctx.pathParam("id");
        return todoStore.findById(id);
    }))
    .codec(new JsonCodec())
    .codec(new XmlCodec())
    .filter(new AuthFilter())
    .build();

registry.register(todoService);
```

## Build

```bash
# Compile (includes dependencies: blocks, service, http)
mvn compile -pl web-services -am

# Run tests
mvn test -pl web-services
```

## Module Dependencies

```
blocks → service → http → web-services
```

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
