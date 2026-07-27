# GraphQL Module -- Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 135
- **Dependencies**: http (HTTP transport, WebSocket)
- **Standards**: GraphQL Specification (June 2018), GraphQL over HTTP, graphql-transport-ws

---

## Requirements

### Type System
1. Implement a sealed `GraphQLType` interface with exactly 8 permitted implementations
2. Named types: `ScalarType`, `ObjectType`, `InterfaceType`, `UnionType`, `EnumType`, `InputObjectType`
3. Wrapping types: `ListType`, `NonNullType`
4. Built-in scalars: Int (32-bit signed integer), Float (double-precision), String (UTF-8), Boolean, ID (serialized as String)
5. Custom scalars with user-defined serialize and parse functions
6. Object types with fields, arguments, and interface implementation lists
7. Interface types with fields and tracked implementing object types
8. Union types with member object types and membership checking
9. Enum types with values supporting deprecation
10. Input object types with input field definitions and default values
11. Type classification: isInputType(), isOutputType(), isNamedType(), isWrappingType(), unwrap()

### Schema Definition
1. `GraphQLSchema` with required query root type, optional mutation and subscription root types
2. Builder pattern for schema construction
3. Auto-collect referenced types by traversing root type fields recursively
4. Register interface implementations during schema build
5. Built-in directives: @skip, @include, @deprecated with proper location restrictions
6. Custom directive definitions with argument definitions and location sets
7. Schema description support

### Schema Definition Language (SDL)
1. Parse SDL strings into `GraphQLSchema` via `SchemaParser`
2. Support all SDL constructs: schema definition, scalar, type, interface, union, enum, input, directive
3. Forward reference resolution via two-pass parsing (collect then resolve)
4. Handle `implements` with multiple interfaces (ampersand-separated)
5. Handle union members with pipe-separated types
6. Handle directive locations on definitions
7. Print schema back to SDL via `SchemaPrinter` with proper formatting
8. Support descriptions on types and fields

### Query Language (Lexer)
1. Tokenize all GraphQL punctuators: `!`, `$`, `&`, `(`, `)`, `:`, `=`, `@`, `[`, `]`, `{`, `}`, `|`, `...`
2. Tokenize names (identifiers): `[_A-Za-z][_0-9A-Za-z]*`
3. Tokenize integer values (with optional negative sign)
4. Tokenize float values (decimal point, exponent notation)
5. Tokenize single-line string values with escape sequences (`\"`, `\\`, `\/`, `\b`, `\f`, `\n`, `\r`, `\t`, `\uXXXX`)
6. Tokenize block strings (`"""..."""`) with common indent stripping and blank line trimming
7. Skip whitespace, commas, and BOM characters
8. Skip comments (`#` to end of line)
9. Track line and column numbers for error reporting

### Query Language (Parser)
1. Parse operation definitions (query, mutation, subscription) with optional name
2. Parse anonymous query shorthand (bare selection set)
3. Parse variable definitions with type references and default values
4. Parse selection sets with fields, fragment spreads, and inline fragments
5. Parse field aliases
6. Parse field arguments with all value types
7. Parse directives on operations, fields, fragments, inline fragments
8. Parse fragment definitions with type conditions
9. Parse all value types: Int, Float, String, Boolean, Null, Enum, Variable, List, Object
10. Produce a `Document` AST with operations and fragments

### Query Validation
1. Unique operation names within a document
2. Unique fragment names within a document
3. Fragment type conditions must reference valid composite types (object, interface, union)
4. No unused fragments
5. Fields must exist on the queried type
6. Known argument names on fields
7. Leaf types (scalar, enum) must not have sub-selections
8. Composite types (object, interface, union) must have sub-selections
9. Unknown fragment references detected
10. Unique variable names per operation
11. All used variables must be defined
12. Pluggable validation rules via `ValidationRule` functional interface

### Execution Engine
1. Parse query string or accept pre-parsed `Document`
2. Validate document before execution
3. Resolve operations by name (or use single operation)
4. Execute query selection sets with field collection and merging
5. Execute mutations serially (one field at a time, per specification)
6. Resolve field arguments with variable substitution and default values
7. Invoke `DataFetcher` resolvers for fields, or fall back to default property-based fetcher
8. Complete values by type: NonNull (null propagation), List (iterate), Scalar (serialize), Enum (validate), Object (recurse), Interface/Union (resolve then recurse)
9. Abstract type resolution: `__typename` Map key, class name matching, single possible type, first possible type fallback
10. Collect errors with field path during execution
11. Return partial results (data + errors) per specification
12. Handle `@skip` and `@include` directives with literal and variable arguments

### Introspection
1. `__schema` field on query root returning schema metadata
2. `__type(name: String!)` field on query root returning type metadata
3. `__typename` field on all object types returning the type name
4. All 8 introspection types: `__Schema`, `__Type`, `__Field`, `__InputValue`, `__EnumValue`, `__Directive`, `__TypeKind`, `__DirectiveLocation`
5. Type kind enumeration: SCALAR, OBJECT, INTERFACE, UNION, ENUM, INPUT_OBJECT, LIST, NON_NULL
6. Directive location enumeration: all 19 locations (7 executable + 12 type system)
7. Deprecation information on fields and enum values

### Subscriptions
1. `SubscriptionPublisher<T>` wrapping `java.util.concurrent.SubmissionPublisher`
2. Publish events to all subscribers
3. Subscribe via `Flow.Subscriber` or simple `Consumer<T>` callback
4. Unsubscribe support (returned `Runnable` for Consumer subscribers)
5. Subscriber count tracking
6. AutoCloseable for cleanup

### HTTP Transport
1. Server handler implementing `HttpRequestHandler`
2. POST with `application/json` body: parse `{query, operationName, variables}`
3. POST with `application/graphql` body: raw query string
4. GET with query parameters: `query`, `operationName`, `variables` (URL-encoded JSON)
5. Response with `application/graphql+json; charset=utf-8` content type
6. Error responses for missing query, syntax errors, internal errors
7. Client using `java.net.http.HttpClient` with POST JSON
8. Custom headers support on client

### WebSocket Transport
1. Server handler implementing graphql-transport-ws protocol
2. `connection_init` / `connection_ack` handshake
3. `subscribe` with operation payload / `next` with result / `error` / `complete`
4. `ping` / `pong` keep-alive
5. Active subscription tracking with `ConcurrentHashMap`
6. Subscription cleanup on `complete` message or session close
7. Client with WebSocket connection, `graphql-transport-ws` subprotocol
8. Client `subscribe()` returning subscription ID for later `unsubscribe()`
9. Pending query tracking with `CompletableFuture` and 30-second timeout

### Demo Applications
1. StarWarsSchema: Character interface, Human/Droid types, Episode enum, SearchResult union, hero/human/droid/search queries with data fetchers
2. TodoSchema: CRUD mutations (addTodo, toggleTodo, deleteTodo, clearCompleted), TodoInput input object, TodoStatus enum filter, stateful with ConcurrentHashMap
3. ChatSchema: subscriptions (newMessage), sendMessage mutation, SubscriptionPublisher integration, message history query with room filtering and limits
4. `DemoGraphqlAll`: comprehensive demo exercising all 13 major features (schema definition, query execution, mutations, variables/arguments, fragments, directives, interfaces/unions, introspection, validation, SDL round-trip, subscriptions, error handling, JSON codec) with `Results` record for programmatic verification

---

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Compliance](COMPLIANCE.md)
- [Root README](../../../README.md) | [Root Architecture](../../../doc/ARCHITECTURE.md)

---

**Last Updated**: 2026-07-07
