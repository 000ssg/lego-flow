# gRPC Module -- Architecture

This document describes the architectural decisions for the gRPC module.

---

## Protocol Overview

gRPC is a high-performance RPC framework using HTTP/2 for transport and Protocol Buffers for message serialization. The Lego Flow implementation provides a pure-JDK gRPC stack with runtime protobuf encoding -- no `.proto` file compilation or code generation required. All message and service descriptors are built programmatically.

## Layered Architecture

```mermaid
graph TD
    L1["Client (GrpcStub, GrpcChannel)<br/>/ Server (GrpcServer, GrpcServiceRegistry)<br/>(call dispatch, interceptor chains, loopback transport)"]
    L2["Call Handling<br/>(ClientCall, ServerCall, CallOptions,<br/>UnaryHandler, StreamHandler)"]
    L3["Metadata<br/>(Metadata, MetadataKey,<br/>string + binary keys, case-insensitive)"]
    L4["gRPC Transport<br/>(GrpcFrameCodec, GrpcHeaders, GrpcStatus,<br/>GrpcTimeout, GrpcEncoding, compression)"]
    L5["Protobuf Codec<br/>(ProtobufCodec, ProtoMessage, FieldValue,<br/>varint/zigzag/fixed/length-delimited/packed/map/oneof)"]
    L6["Runtime Descriptors<br/>(MessageDescriptor, FieldDescriptor,<br/>ServiceDescriptor, MethodDescriptor)"]

    L1 --> L2 --> L3
    L2 --> L4
    L4 --> L5 --> L6
```

## Protobuf Wire Format

The protobuf layer encodes and decodes binary protobuf messages from scratch, supporting all standard wire types:

```mermaid
graph LR
    PM["ProtoMessage<br/>(field number -> FieldValue)"] --> Codec["ProtobufCodec<br/>encode / decode"]
    Codec --> Binary["Binary protobuf bytes"]
    
    subgraph "FieldValue (sealed interface)"
        FV1["VarintValue<br/>int32/64, uint32/64,<br/>sint32/64, bool, enum"]
        FV2["Fixed64Value<br/>fixed64, sfixed64, double"]
        FV3["Fixed32Value<br/>fixed32, sfixed32, float"]
        FV4["BytesValue<br/>string, bytes"]
        FV5["MessageValue<br/>embedded messages"]
        FV6["RepeatedValue<br/>repeated fields"]
        FV7["MapValue<br/>map entries"]
    end
```

### Encoding Details

| Encoding | Wire Type | Field Types |
|----------|-----------|-------------|
| Varint (LEB128) | 0 | int32, int64, uint32, uint64, bool, enum |
| ZigZag + Varint | 0 | sint32, sint64 |
| Fixed 32-bit LE | 5 | fixed32, sfixed32, float |
| Fixed 64-bit LE | 1 | fixed64, sfixed64, double |
| Length-delimited | 2 | string, bytes, embedded message, packed repeated |

### Runtime Descriptor System

Instead of compiled `.proto` files, descriptors are built at runtime:

```mermaid
graph TD
    SD["ServiceDescriptor<br/>(full name, methods)"]
    MD["MethodDescriptor<br/>(path, type, request/response descriptors)"]
    MsgD["MessageDescriptor<br/>(full name, fields by number/name, oneofs)"]
    FD["FieldDescriptor<br/>(number, name, type, repeated, packed,<br/>map key/value, message descriptor, oneof index)"]

    SD -->|"contains"| MD
    MD -->|"request/response"| MsgD
    MsgD -->|"contains"| FD
    FD -->|"nested message"| MsgD
```

## gRPC Frame Format

Messages are framed with a 5-byte header for transmission over HTTP/2:

```mermaid
graph LR
    subgraph "gRPC Frame (5 + N bytes)"
        CF["Compressed<br/>Flag<br/>(1 byte)"]
        LEN["Message<br/>Length<br/>(4 bytes BE)"]
        DATA["Protobuf<br/>Message<br/>(N bytes)"]
    end
    CF --- LEN --- DATA
```

- Compressed flag: `0` = identity, `1` = compressed (gzip or deflate)
- Length: 4-byte big-endian unsigned integer
- Default max message size: 4 MB
- Compression support: identity, gzip, deflate

## Request/Response Flow

### Unary Call

```mermaid
sequenceDiagram
    participant Client as GrpcStub / GrpcChannel
    participant Server as GrpcServer

    Client->>Client: Encode request (ProtobufCodec + GrpcFrameCodec)
    Client->>Server: POST /service/Method + framed request
    Server->>Server: Lookup method in GrpcServiceRegistry
    Server->>Server: Apply ServerInterceptor chain
    Server->>Server: Decode request, invoke UnaryHandler
    Server->>Server: Encode response (ProtobufCodec + GrpcFrameCodec)
    Server-->>Client: HTTP 200 + framed response + trailers (grpc-status=0)
    Client->>Client: Process trailers, decode response
```

### Server Streaming Call

```mermaid
sequenceDiagram
    participant Client as GrpcStub / GrpcChannel
    participant Server as GrpcServer

    Client->>Server: POST /service/Method + framed request
    Server->>Server: Decode request, invoke ServerStreaming handler
    Server-->>Client: Response frame 1
    Server-->>Client: Response frame 2
    Server-->>Client: Response frame N
    Server-->>Client: Trailers (grpc-status=0)
    Client->>Client: Decode all response frames
```

### Client Streaming Call

```mermaid
sequenceDiagram
    participant Client as GrpcStub / GrpcChannel
    participant Server as GrpcServer

    Client->>Server: Framed request 1 + request 2 + ... + request N
    Server->>Server: Decode all request frames
    Server->>Server: Invoke ClientStreaming handler
    Server-->>Client: Single framed response + trailers
```

### Bidi Streaming Call

```mermaid
sequenceDiagram
    participant Client as GrpcStub / GrpcChannel
    participant Server as GrpcServer

    Client->>Server: Framed request 1 + request 2 + ... + request N
    Server->>Server: Decode all request frames
    Server->>Server: Invoke BidiStreaming handler
    Server-->>Client: Response frame 1
    Server-->>Client: Response frame 2
    Server-->>Client: Response frame M
    Server-->>Client: Trailers (grpc-status=0)
```

## Server Architecture

```mermaid
graph TD
    REQ["Incoming Request<br/>(path + framed data + metadata)"] --> GS["GrpcServer.processRequest()"]
    GS --> REG["GrpcServiceRegistry<br/>(ConcurrentHashMap lookup)"]
    REG --> MD["MethodDescriptor<br/>(call type, descriptors)"]
    GS --> INT["ServerInterceptor Chain"]
    INT --> SC["ServerCall<br/>(response collection)"]
    
    SC --> DISPATCH{Method Type?}
    DISPATCH -->|UNARY| UH["UnaryHandler"]
    DISPATCH -->|SERVER_STREAMING| SSH["StreamHandler.ServerStreaming"]
    DISPATCH -->|CLIENT_STREAMING| CSH["StreamHandler.ClientStreaming"]
    DISPATCH -->|BIDI_STREAMING| BSH["StreamHandler.BidiStreaming"]
    
    UH --> RESULT["ServerCallResult<br/>(status, frames, trailers)"]
    SSH --> RESULT
    CSH --> RESULT
    BSH --> RESULT
```

## Client Architecture

```mermaid
graph TD
    STUB["GrpcStub<br/>(service-specific, method resolution)"]
    STUB --> INT["ClientInterceptor Chain"]
    INT --> CH["GrpcChannel"]
    CH --> CC["ClientCall<br/>(encode request, build headers)"]
    
    CH -->|Loopback| LP["GrpcServer.processRequest()<br/>(direct in-process dispatch)"]
    CH -->|Remote| HTTP2["HTTP/2 Frame Exchange<br/>(paired Http2Connections,<br/>HPACK, gRPC framing)"]
    
    LP --> PROC["processResponse() + processTrailers()"]
    HTTP2 --> FRAMES["performHttp2Exchange()<br/>HEADERS + DATA frames"]
    FRAMES --> RPROC["processResponseFrames()<br/>decode HEADERS/DATA/trailers"]
    PROC --> RESP["ProtoMessage response(s)"]
    RPROC --> RESP
```

- **Loopback mode**: `GrpcChannel(server)` dispatches directly to `GrpcServer.processRequest()`, enabling full end-to-end testing without network I/O
- **Remote mode**: `GrpcChannel(authority)` constructs paired client/server `Http2Connection` instances, exchanges connection preface and SETTINGS, then sends gRPC request as HEADERS + DATA frames and processes the server's response HEADERS, DATA, and trailer frames. The frame exchange uses HPACK header compression, gRPC pseudo-headers (`:method=POST`, `:path=/service/method`, `content-type=application/grpc`, `te=trailers`), and length-prefixed protobuf message framing. Without a loopback server, returns `UNAVAILABLE` status; for production use, the generated frames can be serialized to the wire via `Http2FrameCodec`.

## Interceptor Architecture

Both server and client interceptors are `@FunctionalInterface`:

```mermaid
graph LR
    subgraph "Server Side"
        SI1["Interceptor 1"] --> SI2["Interceptor 2"] --> SH["Handler"]
    end
    
    subgraph "Client Side"
        CI1["Interceptor 1"] --> CI2["Interceptor 2"] --> CALL["ClientCall"]
    end
```

- **ServerInterceptor**: `intercept(MethodDescriptor, Metadata, ServerCall) -> ServerCall`
- **ClientInterceptor**: `intercept(MethodDescriptor, CallOptions, Metadata, ClientCall) -> ClientCall`
- Interceptors can modify metadata, add logging, enforce authentication, etc.

## Metadata System

```mermaid
graph TD
    META["Metadata<br/>(LinkedHashMap, case-insensitive keys)"]
    META --> SK["MetadataKey.StringKey<br/>(text values, no -bin suffix)"]
    META --> BK["MetadataKey.BinaryKey<br/>(base64-encoded, -bin suffix)"]
    META --> MV["Multi-value support<br/>(List per key)"]
    META --> MERGE["merge(other) for combining"]
```

- Keys are normalized to lowercase
- Binary keys (suffix `-bin`) use Base64 encoding/decoding
- `GrpcHeaders.extractMetadata()` filters out gRPC-specific headers

## Thread Safety

- `GrpcServiceRegistry` uses `ConcurrentHashMap` for all handler and descriptor maps
- `GrpcServer.interceptors` uses `CopyOnWriteArrayList` for safe concurrent reads
- `ServerCall` and `ClientCall` are per-request instances (no shared mutable state)
- `ProtoMessage` is mutable but intended for single-threaded use within a call

## Integration with Lego Flow

| Lego Flow Module | Usage in gRPC |
|------------------|---------------|
| `web/http` | `HttpHeaders` from `ssg.legoflow.http.core` used for gRPC header/trailer construction |

The gRPC module is self-contained for protobuf and gRPC framing. Remote gRPC calls use the `web/http2` module for HTTP/2 connection management, HPACK header compression, and frame construction.

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)

---

**Last Updated**: 2026-07-07
