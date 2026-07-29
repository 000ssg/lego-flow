# rpc / grpc — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `grpc` module implements the gRPC protocol (HTTP/2 + protobuf wire format) with runtime descriptor-based encoding. No external dependencies, no `.proto` code generation -- all message and service descriptors are built programmatically at runtime.

## Key Interfaces

- `ProtobufCodec` -- binary codec for all protobuf wire types (varint, zigzag, fixed32/64, length-delimited, packed, maps, oneofs, nested messages)
- `GrpcFrameCodec` -- gRPC length-prefixed message framing (1-byte compressed flag + 4-byte length + data)
- `GrpcServer` -- server with service registry, interceptor chain, request dispatch for all four call types
- `GrpcChannel` -- client connection (loopback mode for testing, authority-based for remote)
- `GrpcStub` -- dynamic stub wrapping a channel and service descriptor for convenient RPC calls
- `Metadata` -- typed key-value metadata for headers and trailers (string and binary `-bin` keys)
- `ServerInterceptor` / `ClientInterceptor` -- functional interceptor chains for server and client sides

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protobuf` | Protobuf wire format: varint (LEB128), zigzag, all wire types, `ProtoMessage` (dynamic field map), `ProtobufCodec` (encode/decode), `FieldDescriptor`/`MessageDescriptor`/`ServiceDescriptor` (runtime schema), `FieldValue` (sealed interface for typed values), `FieldTag`, `WireType` |
| `transport` | gRPC framing over HTTP/2: `GrpcFrameCodec` (length-prefixed frames with gzip/deflate compression), `GrpcHeaders` (standard headers/trailers/pseudo-headers, percent encoding), `GrpcStatus` (all 17 status codes), `GrpcTimeout` (nS/uS/mS/S/M/H format), `GrpcEncoding` (identity/gzip/deflate) |
| `metadata` | Typed metadata: `Metadata` (case-insensitive key-value map, multi-value, merge), `MetadataKey` (sealed: `StringKey` for text, `BinaryKey` with base64 for `-bin` keys) |
| `common` | Shared types: `MethodDescriptor` (full path, call type, request/response descriptors), `MethodType` (UNARY, SERVER_STREAMING, CLIENT_STREAMING, BIDI_STREAMING), `StatusException` (status + message + trailing metadata) |
| `server` | Server-side: `GrpcServer` (registry, interceptors, dispatch, encode/decode), `GrpcServiceRegistry` (concurrent maps for services and handlers), `ServerCall` (call state, response collection), `UnaryHandler` / `StreamHandler` (functional handler interfaces), `ServerInterceptor` |
| `client` | Client-side: `GrpcChannel` (loopback or authority-based), `GrpcStub` (dynamic stub per service), `ClientCall` (request encoding, response parsing, trailer processing), `CallOptions` (timeout, encoding, metadata, authority, max size), `ClientInterceptor` |
| `demo` | Demo services: `CalculatorService` (unary: add/multiply/divide), `FileDownloadService` (server streaming: chunked download), `UploadService` (client streaming: upload with CRC32 checksum), `ChatService` (bidi streaming: echo + system notifications) |

## gRPC-Specific Coding Conventions

### Call Types
- **Unary**: single `ProtoMessage` request, single `ProtoMessage` response (`UnaryHandler`)
- **Server Streaming**: single request, multiple responses via `Consumer<ProtoMessage>` (`StreamHandler.ServerStreaming`)
- **Client Streaming**: `List<ProtoMessage>` requests, single response (`StreamHandler.ClientStreaming`)
- **Bidi Streaming**: `List<ProtoMessage>` requests, multiple responses via `Consumer<ProtoMessage>` (`StreamHandler.BidiStreaming`)

### Runtime Descriptors (no .proto files)
- `MessageDescriptor.builder("Name").addField(...).build()` for message schemas
- `ServiceDescriptor.builder("package.Service").addMethod(...).build()` for service schemas
- `FieldDescriptor.scalar(number, name, type)` / `.repeated(...)` / `.message(...)` / `.map(...)` / `.oneof(...)` for field metadata
- `ProtobufCodec.encode(msg, descriptor)` and `ProtobufCodec.decode(data, descriptor)` for schema-aware encoding
- Schema-less encoding/decoding also supported (wire types inferred from `FieldValue` subtypes)

### Protobuf Wire Types
- `VARINT` (0): int32, int64, uint32, uint64, sint32, sint64, bool, enum
- `FIXED64` (1): fixed64, sfixed64, double
- `LENGTH_DELIMITED` (2): string, bytes, embedded messages, packed repeated
- `START_GROUP` (3), `END_GROUP` (4): deprecated, throw `UnsupportedOperationException`
- `FIXED32` (5): fixed32, sfixed32, float

### Status Codes
All 17 gRPC status codes are in `GrpcStatus`: OK (0), CANCELLED (1), UNKNOWN (2), INVALID_ARGUMENT (3), DEADLINE_EXCEEDED (4), NOT_FOUND (5), ALREADY_EXISTS (6), PERMISSION_DENIED (7), RESOURCE_EXHAUSTED (8), FAILED_PRECONDITION (9), ABORTED (10), OUT_OF_RANGE (11), UNIMPLEMENTED (12), INTERNAL (13), UNAVAILABLE (14), DATA_LOSS (15), UNAUTHENTICATED (16).

### Headers and Trailers
- Request headers: `:method=POST`, `:path=/package.Service/Method`, `content-type=application/grpc`, `te=trailers`, `grpc-encoding`, `grpc-timeout`
- Response headers: `:status=200`, `content-type=application/grpc`, `grpc-encoding`
- Trailers: `grpc-status`, `grpc-message` (percent-encoded)

### Key Patterns
- `FieldValue` is a sealed interface with records: `VarintValue`, `Fixed64Value`, `Fixed32Value`, `BytesValue`, `RepeatedValue`, `MapValue`, `MessageValue`
- `ProtoMessage` is a mutable `LinkedHashMap<Integer, FieldValue>` with typed getters/setters
- Handlers are `@FunctionalInterface` -- lambdas used for service registration
- `GrpcServiceRegistry` uses `ConcurrentHashMap` for thread safety
- `ServerCall` collects response messages via `Consumer<ProtoMessage>` pattern
- `GrpcChannel` loopback mode directly calls `GrpcServer.processRequest()` for testing
- `GrpcChannel` remote mode constructs paired `Http2Connection` instances, exchanges SETTINGS, builds HEADERS + DATA frames via HPACK, and processes response frames; without a loopback server returns `UNAVAILABLE`

## Testing Practices

- Protobuf codec tests: encode/decode round-trip for every wire type (varint, zigzag, fixed32/64, string, bytes, nested messages, repeated, packed, maps, oneofs)
- Transport tests: frame encoding/decoding, compression (gzip, deflate), status codes, headers, timeout parsing
- Metadata tests: case-insensitive keys, multi-value, binary keys with base64, merge
- Server tests: all four call types, interceptor chains, error handling, status propagation
- Client tests: channel, stub, call options, request/response encoding, trailer processing
- Demo tests: end-to-end for Calculator (unary), FileDownload (server streaming), Upload (client streaming), Chat (bidi streaming)
- All tests use loopback transport (no external server required)
- Test count: 298
