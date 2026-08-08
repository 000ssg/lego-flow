# rpc / graphql — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `graphql` module implements a GraphQL query language engine for the Lego Flow framework. It provides a complete GraphQL pipeline: schema definition (programmatic and SDL), query parsing, validation, execution with resolvers, introspection, and transport bindings for HTTP and WebSocket (subscriptions via the graphql-transport-ws protocol).

## Key Interfaces

- `GraphQLSchema` — schema container with query/mutation/subscription root types, type map, directives; built via `Builder`
- `GraphQLType` — sealed interface for all 8 type kinds: `ScalarType`, `ObjectType`, `InterfaceType`, `UnionType`, `EnumType`, `InputObjectType`, `ListType`, `NonNullType`
- `ExecutionEngine` — validates and executes parsed documents against a schema; serial execution for mutations, selection-set execution for queries
- `GraphQLParser` — parses GraphQL query strings into `Document` AST (operations + fragments)
- `GraphQLLexer` — tokenizes GraphQL source strings (all punctuators, names, numbers, strings, block strings, comments)
- `QueryValidator` — validates documents against a schema using pluggable `ValidationRule` functions
- `DataFetcher<T>` — functional interface for field resolvers; receives `DataFetchingEnvironment`
- `SubscriptionPublisher<T>` — wraps `SubmissionPublisher` for subscription event delivery
- `GraphQLTransport` — SPI for transport bindings (HTTP, WebSocket)
- `SchemaParser` — parses SDL strings into `GraphQLSchema` (two-pass: collect types, resolve references)
- `SchemaPrinter` — serializes a `GraphQLSchema` back to SDL
- `IntrospectionResolver` — adds `__schema` and `__type` fields to the query root with data fetchers
- `JsonCodec` — zero-dependency JSON encoder/decoder for transport layer

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `schema` | Type system: `GraphQLSchema`, `GraphQLType` (sealed, 8 permits), `ObjectType`, `InterfaceType`, `UnionType`, `EnumType`, `ScalarType`, `InputObjectType`, `ListType`, `NonNullType`, `FieldDefinition`, `ArgumentDefinition`, `Directive` |
| `language` | Query language: `GraphQLLexer` (tokenizer), `GraphQLParser` (recursive descent), AST nodes (`Document`, `OperationDefinition`, `SelectionSet`, `Field`, `FragmentDefinition`, `FragmentSpread`, `InlineFragment`, `Value`, `VariableDefinition`, `Token`) |
| `execution` | Execution engine: `ExecutionEngine` (validate + execute), `ExecutionContext` (per-execution state), `ExecutionResult` (data + errors), `DataFetcher<T>` (resolver SPI), `DataFetchingEnvironment`, `SubscriptionPublisher<T>` |
| `validation` | Query validation: `QueryValidator` (default rules), `ValidationRule` (functional interface), `ValidationError` (record) |
| `introspection` | Introspection system: `IntrospectionResolver` (field/type resolvers), `IntrospectionTypes` (8 introspection types: `__Schema`, `__Type`, `__Field`, `__InputValue`, `__EnumValue`, `__Directive`, `__TypeKind`, `__DirectiveLocation`) |
| `sdl` | Schema Definition Language: `SchemaParser` (SDL to schema, two-pass with forward reference resolution), `SchemaPrinter` (schema to SDL) |
| `transport` | Transport SPI: `GraphQLTransport` (interface), `JsonCodec` (encode/decode) |
| `transport.http` | HTTP transport: `GraphQLHttpHandler` (server-side, POST JSON + POST graphql + GET), `GraphQLHttpClient` (client-side, POST with java.net.http) |
| `transport.websocket` | WebSocket transport: `GraphQLWebSocketHandler` (graphql-transport-ws protocol: connection_init/ack, subscribe/next/error/complete, ping/pong), `GraphQLWebSocketClient` (client with connection handshake, subscribe, unsubscribe) |
| `demo` | Demo schemas: `StarWarsSchema` (interfaces, unions, enums), `TodoSchema` (CRUD mutations, input objects), `ChatSchema` (subscriptions, SubscriptionPublisher) |

## GraphQL-Specific Coding Conventions

### Type System (sealed interface)
- `GraphQLType` is sealed, permitting exactly 8 implementations
- Named types: `ScalarType`, `ObjectType`, `InterfaceType`, `UnionType`, `EnumType`, `InputObjectType`
- Wrapping types: `ListType`, `NonNullType`
- Pattern matching via `switch` expressions is used throughout (execution, introspection, SDL)
- Built-in scalars: `Int`, `Float`, `String`, `Boolean`, `ID` (constants on `ScalarType`)
- Built-in directives: `@skip`, `@include`, `@deprecated` (constants on `Directive`)

### Execution Model
- **Queries**: `executeSelectionSet()` — collects fields, resolves each, completes values by type
- **Mutations**: `executeSerially()` — same as queries but fields execute one at a time (per spec)
- **Subscriptions**: `SubscriptionPublisher<T>` wraps `java.util.concurrent.SubmissionPublisher`
- **Abstract type resolution**: `__typename` key in Map, class name matching, or first possible type
- **Null propagation**: non-null fields propagate null upward per GraphQL spec
- **Default data fetcher**: Map.get, then methodName(), getMethodName(), isMethodName(), field access

### Validation Rules (5 default)
1. `UniqueOperationNames` — no duplicate operation names
2. `UniqueFragmentNames` — no duplicate fragment names
3. `FragmentUsage` — fragment type conditions valid, no unused fragments
4. `FieldsOnCorrectType` — fields exist on the type, arguments known, leaf/composite checks
5. `Variables` — unique names, all used variables defined

### SDL Parser (Two-Pass)
- First pass: collect all type definitions (schema, scalar, type, interface, union, enum, input, directive)
- Second pass: resolve forward references (object interfaces, union members, field type references)
- Pre-registers built-in scalars; uses placeholder ScalarType for forward references

### Transport Protocols
- **HTTP**: POST application/json (`{query, operationName, variables}`), POST application/graphql (query body), GET with query params
- **WebSocket**: graphql-transport-ws protocol (`connection_init` / `connection_ack`, `subscribe` / `next` / `error` / `complete`, `ping` / `pong`)
- Response content type: `application/graphql+json; charset=utf-8`

## Testing Practices

- Lexer tests: all token types, punctuators, strings, block strings, numbers, comments, BOM, unicode escapes
- Parser tests: operations, fragments, variables, directives, inline fragments, anonymous queries, all value types, syntax errors
- Schema tests: builder, type map, directives, possible types, type collection, interface implementations
- Type tests: all 8 type kinds, wrapping/unwrapping, input/output classification, sealed interface properties
- Execution tests: simple queries, nested fields, arguments, aliases, variables, default values, null propagation, error handling, partial results, introspection (`__schema`, `__type`, `__typename`), Star Wars demo
- Directive execution tests: `@skip`, `@include` with literal and variable arguments
- Interface/union execution tests: abstract type resolution, `__typename`, inline fragments on unions
- Mutation execution tests: serial execution, input objects, CRUD operations
- Validation tests: all 5 rules, valid and invalid queries, edge cases
- DemoGraphqlAll test: comprehensive 13-section demo covering all major GraphQL features with `Results` record for programmatic verification
- All tests use in-memory schemas (no external services required)
- Test count: 135
