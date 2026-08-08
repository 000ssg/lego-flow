
# Lego Flow GraphQL Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-135-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

GraphQL query language module for the Lego Flow framework, providing schema definition, query parsing, validation, execution, introspection, and transport bindings over HTTP and WebSocket.

## Overview

This module implements a complete GraphQL engine following the GraphQL specification (June 2018). It provides programmatic and SDL-based schema definition, a recursive-descent parser for the query language, validation against schema rules, an execution engine with resolver support, full introspection, and transport layers for HTTP (queries/mutations) and WebSocket (subscriptions via graphql-transport-ws). The architecture layers cleanly from transport down to the type system:

```
HTTP Handler / WebSocket Handler (transport layer)
  -> JSON Codec (request/response serialization)
    -> Execution Engine (validate, resolve, complete)
      -> Query Validator (5 validation rules)
        -> GraphQL Parser (recursive descent, AST)
          -> GraphQL Lexer (tokenization)
      -> Schema (type system, directives, introspection)
```

## Features

- **Full type system** -- all 8 GraphQL types: Scalar, Object, Interface, Union, Enum, InputObject, List, NonNull (sealed interface with pattern matching)
- **Built-in scalars** -- Int, Float, String, Boolean, ID with coercion
- **Schema Definition Language** -- parse SDL to schema and print schema back to SDL
- **Programmatic schema building** -- fluent builder API for constructing schemas in code
- **Query parsing** -- full GraphQL query language: operations, fragments, variables, directives, aliases, all value types (int, float, string, block string, boolean, null, enum, list, object)
- **Query validation** -- 5 rules: unique operations/fragments, fragment usage, field existence, variable definitions
- **Execution engine** -- selection set resolution, field collection, argument resolution, variable substitution, null propagation
- **Serial mutations** -- mutation fields execute sequentially per the GraphQL specification
- **Subscriptions** -- `SubscriptionPublisher<T>` backed by `java.util.concurrent.SubmissionPublisher`
- **Abstract type resolution** -- interface and union types resolved via `__typename`, class name matching, or possible types
- **Introspection** -- `__schema`, `__type`, `__typename` with all introspection types (`__Schema`, `__Type`, `__Field`, `__InputValue`, `__EnumValue`, `__Directive`, `__TypeKind`, `__DirectiveLocation`)
- **Directives** -- `@skip`, `@include`, `@deprecated` built-in; custom directive definitions supported
- **HTTP transport** -- server handler (POST JSON, POST graphql, GET) and client (POST with java.net.http.HttpClient)
- **WebSocket transport** -- graphql-transport-ws protocol (connection_init/ack, subscribe/next/error/complete, ping/pong)
- **JSON codec** -- zero-dependency JSON encoder/decoder for transport
- **Default data fetcher** -- property-based resolution: Map.get, method(), getMethod(), isMethod(), field access

## Quick Start

### Define a schema programmatically

```java
var todoType = ObjectType.of("Todo", List.of(
    FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
    FieldDefinition.of("title", NonNullType.of(ScalarType.STRING)),
    FieldDefinition.of("completed", NonNullType.of(ScalarType.BOOLEAN))
));

var todosField = FieldDefinition.of("todos",
    NonNullType.of(ListType.of(NonNullType.of(todoType))));
todosField.dataFetcher(env -> todoService.getAll());

var queryType = ObjectType.of("Query", List.of(todosField));
var schema = GraphQLSchema.newSchema().query(queryType).build();
```

### Parse a schema from SDL

```java
var schema = SchemaParser.parse("""
    type Query {
        hero(episode: Episode): Character
        human(id: ID!): Human
    }
    interface Character {
        id: ID!
        name: String!
    }
    type Human implements Character {
        id: ID!
        name: String!
        homePlanet: String
    }
    enum Episode { NEWHOPE EMPIRE JEDI }
    """);
```

### Execute a query

```java
var engine = new ExecutionEngine(schema);
var result = engine.execute(
    "{ hero(episode: EMPIRE) { name } }",
    null, null, null);

Map<String, Object> data = result.getData();
// data = {"hero": {"name": "R2-D2"}}
```

### Execute a mutation

```java
var result = engine.execute("""
    mutation {
        addTodo(input: {title: "Write docs", completed: false}) {
            id
            title
            completed
        }
    }
    """, null, null, null);
```

### HTTP server handler

```java
var handler = new GraphQLHttpHandler(schema);
// Register with Lego Flow HTTP server at /graphql endpoint
```

### WebSocket subscriptions

```java
var wsHandler = new GraphQLWebSocketHandler(schema);
// On WebSocket message:
wsHandler.handleMessage(session, messageJson);
// Register a subscription publisher:
wsHandler.registerSubscription(session, subscriptionId, publisher);
```

## Package Structure

```
ssg.legoflow.rpc.graphql/
├── schema/            -- Type system: GraphQLSchema, GraphQLType (sealed, 8 kinds),
|                         ObjectType, InterfaceType, UnionType, EnumType, ScalarType,
|                         InputObjectType, ListType, NonNullType, FieldDefinition,
|                         ArgumentDefinition, Directive
├── language/          -- Query language: GraphQLLexer, GraphQLParser, AST nodes
|                         (Document, OperationDefinition, SelectionSet, Field,
|                         FragmentDefinition, FragmentSpread, InlineFragment, Value,
|                         VariableDefinition, Token, GraphQLSyntaxException)
├── execution/         -- Execution: ExecutionEngine, ExecutionContext, ExecutionResult,
|                         DataFetcher, DataFetchingEnvironment, SubscriptionPublisher
├── validation/        -- Validation: QueryValidator, ValidationRule, ValidationError
├── introspection/     -- Introspection: IntrospectionResolver, IntrospectionTypes
├── sdl/               -- SDL: SchemaParser, SchemaPrinter
├── transport/         -- Transport SPI: GraphQLTransport, JsonCodec
├── transport/http/    -- HTTP: GraphQLHttpHandler (server), GraphQLHttpClient (client)
├── transport/websocket/ -- WebSocket: GraphQLWebSocketHandler, GraphQLWebSocketClient
└── demo/              -- Demo schemas: StarWarsSchema, TodoSchema, ChatSchema
```

## Demo Applications

1. **StarWarsSchema** -- Classic Star Wars example with Character interface, Human/Droid types, Episode enum, SearchResult union, hero/human/droid/search queries
2. **TodoSchema** -- CRUD application with mutations (addTodo, toggleTodo, deleteTodo, clearCompleted), input objects, enum filtering
3. **ChatSchema** -- Chat room with subscriptions (newMessage), mutations (sendMessage), SubscriptionPublisher integration
4. **DemoGraphqlAll** -- Comprehensive demo exercising all 13 major GraphQL features: schema definition, query execution, mutations, variables/arguments, fragments, directives, interfaces/unions, introspection, validation, SDL round-trip, subscriptions, error handling, and JSON codec

## Dependencies

This module depends on:
- `lego-flow-http` -- HTTP protocol for transport layer (HttpRequestHandler, HttpRequest, HttpResponse, WebSocketSession)

---

**Part of the [Lego Flow](../../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
