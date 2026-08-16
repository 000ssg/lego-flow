# gRPC Module -- Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 298
- **Dependencies**: web/http (HttpHeaders)
- **Standards**: gRPC over HTTP/2 (https://grpc.io/docs/), Protocol Buffers Encoding (https://protobuf.dev/programming-guides/encoding/)

---

## Requirements

### Protobuf Wire Format

1. Encode and decode varint (LEB128) for int32, int64, uint32, uint64, bool, enum
2. Encode and decode zigzag encoding for sint32, sint64
3. Encode and decode fixed-width 32-bit fields (fixed32, sfixed32, float) in little-endian
4. Encode and decode fixed-width 64-bit fields (fixed64, sfixed64, double) in little-endian
5. Encode and decode length-delimited fields (string, bytes, embedded messages)
6. Encode and decode repeated fields (both unpacked and packed encoding)
7. Encode and decode map fields (as repeated key-value message entries with field 1 = key, field 2 = value)
8. Support oneof fields with group index tracking
9. Support nested message fields with recursive encode/decode
10. Provide schema-less encoding/decoding (wire types inferred from FieldValue subtypes)
11. Provide schema-aware encoding/decoding using MessageDescriptor

### Runtime Descriptors

1. `FieldDescriptor` record with field number, name, type, repeated/packed flags, map key/value, message descriptor, oneof index
2. `MessageDescriptor` with builder pattern, field lookup by number and by name, oneof group support
3. `ServiceDescriptor` with builder pattern, method lookup by name, full name and simple name accessors
4. `MethodDescriptor` record with full method name, service name, method name, call type, request/response descriptors
5. Factory methods on FieldDescriptor: `scalar()`, `repeated()`, `message()`, `repeatedMessage()`, `map()`, `oneof()`, `oneofMessage()`
6. Factory methods on MethodDescriptor: `unary()`, `serverStreaming()`, `clientStreaming()`, `bidiStreaming()`

### Field Value System

1. Sealed `FieldValue` interface with record subtypes: `VarintValue`, `Fixed64Value`, `Fixed32Value`, `BytesValue`, `RepeatedValue`, `MapValue`, `MessageValue`
2. `ProtoMessage` as mutable `LinkedHashMap<Integer, FieldValue>` with typed getters and setters
3. Convenience methods: `setString()`, `setDouble()`, `setFloat()`, `setBool()`, `setMessage()`, `setRepeated()`, `setMap()` and corresponding getters
4. `fieldNumbers()`, `has()`, `fieldCount()`, `toMap()` for introspection

### gRPC Frame Codec

1. Encode messages into gRPC length-prefixed frames: 1-byte compressed flag + 4-byte big-endian length + data
2. Decode single frames and all frames from ByteBuffer
3. Support compressed frames with gzip and deflate encoding
4. Enforce maximum message size (default 4 MB)
5. Return null on insufficient data (partial frame) for streaming-friendly decoding
6. `decompressIfNeeded()` for transparent decompression based on frame compressed flag

### gRPC Headers and Trailers

1. Construct HTTP/2 request headers: `:method=POST`, `:path`, `:scheme`, `:authority`, `content-type=application/grpc`, `te=trailers`, `grpc-encoding`, `grpc-timeout`
2. Construct HTTP/2 response headers: `:status=200`, `content-type=application/grpc`, `grpc-encoding`
3. Construct trailers: `grpc-status`, `grpc-message` (percent-encoded)
4. Extract status and message from trailers
5. Extract custom metadata from headers (excluding gRPC-specific and pseudo-headers)
6. Validate gRPC content type (`application/grpc`, `application/grpc+proto`, `application/grpc+*`)
7. Percent-encode and percent-decode status messages per gRPC specification

### gRPC Status Codes

1. Implement all 17 status codes: OK (0) through UNAUTHENTICATED (16)
2. Lookup by code value (`fromCode()`)
3. Convenience methods: `isOk()`, `isError()`
4. `StatusException` carrying status code, message, and trailing metadata

### gRPC Timeout

1. Parse and encode `grpc-timeout` header: `<value><unit>` format
2. Support all 6 timeout units: nanoseconds (n), microseconds (u), milliseconds (m), seconds (S), minutes (M), hours (H)
3. Convert to/from `java.time.Duration`
4. Factory methods: `ofSeconds()`, `ofMillis()`, `fromDuration()`, `parse()`

### Compression

1. Support identity (no compression), gzip, and deflate encodings
2. Transparent compression/decompression in frame codec
3. Encoding negotiation via `grpc-encoding` header

### Metadata

1. Case-insensitive key-value map with multi-value support
2. String metadata keys (must not end with `-bin`)
3. Binary metadata keys (must end with `-bin`, values base64-encoded)
4. Typed `MetadataKey<T>` sealed interface with `StringKey` and `BinaryKey` records
5. Merge support for combining metadata from multiple sources
6. Put (replace), add (append), get (first), getAll, remove, containsKey operations

### Server

1. `GrpcServer` with `GrpcServiceRegistry` for service and handler registration
2. Dispatch requests by path to appropriate handler based on method type
3. Support all four call types: unary, server streaming, client streaming, bidi streaming
4. Server interceptor chain (applied before handler invocation)
5. Decode single request frame (unary, server streaming) or multiple frames (client streaming, bidi streaming)
6. Encode response frames with optional compression
7. Return `ServerCallResult` with status, message, metadata, trailers, and response frames
8. Thread-safe service registry using `ConcurrentHashMap`
9. Error handling: `StatusException` mapped to appropriate gRPC status; unexpected exceptions mapped to `INTERNAL`

### Client

1. `GrpcChannel` with loopback mode (dispatch to `GrpcServer`) and remote mode (HTTP/2 frame-based transport)
2. Remote mode uses paired HTTP/2 connections with HPACK header compression, proper gRPC pseudo-headers, and length-prefixed protobuf framing
3. Remote mode constructs HEADERS frame (`:method=POST`, `:path`, `content-type=application/grpc`, `te=trailers`) and DATA frame (gRPC-framed protobuf) per the gRPC-over-HTTP/2 specification
4. Remote mode processes response HEADERS, DATA, and trailer frames to extract response messages and gRPC status
5. `GrpcStub` wrapping channel + service descriptor with method resolution by name
6. `ClientCall` for request encoding, response decoding, and trailer processing
7. `CallOptions` for timeout, encoding, metadata, authority, and max response size
8. Client interceptor chain
9. Support all four call types via `GrpcStub` and `GrpcChannel` in both loopback and remote modes
10. Channel shutdown with closed state tracking

### Demo Services

1. `CalculatorService` -- unary RPCs: add, multiply, divide (with division-by-zero error handling)
2. `FileDownloadService` -- server streaming: chunked file download with simulated content
3. `UploadService` -- client streaming: upload chunks with CRC32 checksum response
4. `ChatService` -- bidi streaming: echo + system notification per incoming message

---

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Compliance](COMPLIANCE.md)

---

**Last Updated**: 2026-07-07

---

## Commit: (planned) — gRPC Cluster Resolver + Load Balancer (Phase 4)

### Original Request
> "investigate cluster-related protocols and choose most popular for each cluster functionality (sharing state, workload balancing, discovery, optimized processing). Cover generic networking as well as HTTP-related activities (supporting web servers cluster). Create plan with reasonable split into phases."

### Reformulated Requirements
1. Define `AddressSource` — SPI for discovering gRPC backend addresses
2. Define `ClusterSubchannel` — represents a connection to one backend (with health state)
3. Define `HealthStatus` — enum for backend health (READY, NOT_SERVING, TRANSIENT_FAILURE, IDLE)
4. Define `GrpcLoadBalancer` — factory and SPI for load balancing strategies
5. Implement `RoundRobinBalancer` — even distribution across healthy backends
6. Implement `LeastRequestBalancer` — route to backend with fewest active requests
7. Implement `ConsistentHashBalancer` — session affinity via consistent hashing
8. Define `GrpcHealthChecker` — periodic health probe for backends
9. All balancers must filter out unhealthy backends
10. gRPC-over-HTTP/2 metadata must be preserved through load balancing

### Final Design Decisions
- **Package:** `ssg.legoflow.rpc.grpc.cluster` (extension to existing gRPC module)
- **Dependencies:** `network/cluster/core` for `ClusterNode` model
- **No external dependencies** — pure load balancing logic
- **Strategy pattern** — each balancer implements `GrpcLoadBalancer` SPI
- **Health-aware** — balancers receive `ClusterSubchannel` with health status
- **Consistent hashing** uses same `MurmurHash3` from cluster core

### Implementation Details
- `AddressSource.java` — SPI for backend address discovery
- `ClusterSubchannel.java` — immutable subchannel with node + health
- `HealthStatus.java` — enum: READY, NOT_SERVING, TRANSIENT_FAILURE, IDLE
- `GrpcLoadBalancer.java` — factory + SPI with `updateChannels()` and `select()`
- `RoundRobinBalancer.java` — atomic counter-based round robin
- `LeastRequestBalancer.java` — selects backend with minimum active requests
- `ConsistentHashBalancer.java` — consistent hash ring with configurable keys
- `GrpcHealthChecker.java` — periodic readiness probe via GRPC health check protocol

### Test Coverage
| Test Class | Coverage |
|-----------|----------|
| `AddressSourceTest` | SPI contract verification |
| `ClusterSubchannelTest` | Construction, equality, health transitions |
| `HealthStatusTest` | Enum values, toString |
| `GrpcLoadBalancerTest` | Factory methods, SPI compliance |
| `RoundRobinBalancerTest` | Even distribution, unhealthy exclusion |
| `LeastRequestBalancerTest` | Active request tracking, dynamic balancing |
| `ConsistentHashBalancerTest` | Key→backend mapping, consistency |
| `GrpcHealthCheckerTest` | Health probe scheduling, state transitions |
| **Tests added**: 8 (in `rpc/grpc/src/test/java/.../cluster/`) |

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~12000 |
| Agent tool calls | ~20 |
| Agent wall time | ~25 min |
| Files created/modified | 18 |
| Lines added/removed | +800 / -5 |
| Tests added | 8 |

---
