# gRPC Protocol Compliance

## Protobuf Wire Format (proto3)

| Feature | Status | Notes |
|---------|--------|-------|
| Varint encoding (LEB128) | Implemented | Full 64-bit support |
| Field tags (field_number << 3 \| wire_type) | Implemented | |
| Wire type 0: Varint | Implemented | int32, int64, uint32, uint64, sint32, sint64, bool, enum |
| Wire type 1: 64-bit | Implemented | fixed64, sfixed64, double |
| Wire type 2: Length-delimited | Implemented | string, bytes, embedded messages, packed repeated |
| Wire type 5: 32-bit | Implemented | fixed32, sfixed32, float |
| Wire type 3/4: Groups | Not supported | Deprecated in proto3 |
| ZigZag encoding | Implemented | sint32, sint64 |
| Nested messages | Implemented | Recursive encoding/decoding |
| Repeated fields | Implemented | Both unpacked and packed |
| Packed repeated fields | Implemented | Varint, fixed32, fixed64 types |
| Map fields | Implemented | Encoded as repeated key-value entries |
| Oneof fields | Implemented | Via oneofIndex in FieldDescriptor |
| Default values | Partial | Zero values not explicitly tracked |
| Unknown fields | Partial | Decoded as raw wire values |

## gRPC over HTTP/2

| Feature | Status | Notes |
|---------|--------|-------|
| Method path: /service/method | Implemented | |
| Content-Type: application/grpc | Implemented | Also application/grpc+proto |
| HTTP/2 POST requests | Implemented | Via request header construction |
| Length-prefixed framing | Implemented | 1-byte flag + 4-byte length + data |
| te: trailers header | Implemented | |
| grpc-encoding header | Implemented | identity, gzip, deflate |
| grpc-timeout header | Implemented | nS, nM, nH, nS, nu, nn format |
| grpc-status trailer | Implemented | All 17 status codes |
| grpc-message trailer | Implemented | With percent-encoding |
| Custom metadata | Implemented | String and binary (-bin) keys |
| Response trailers | Implemented | |

## gRPC Status Codes

All 17 status codes implemented:

| Code | Name | Value |
|------|------|-------|
| 0 | OK | Implemented |
| 1 | CANCELLED | Implemented |
| 2 | UNKNOWN | Implemented |
| 3 | INVALID_ARGUMENT | Implemented |
| 4 | DEADLINE_EXCEEDED | Implemented |
| 5 | NOT_FOUND | Implemented |
| 6 | ALREADY_EXISTS | Implemented |
| 7 | PERMISSION_DENIED | Implemented |
| 8 | RESOURCE_EXHAUSTED | Implemented |
| 9 | FAILED_PRECONDITION | Implemented |
| 10 | ABORTED | Implemented |
| 11 | OUT_OF_RANGE | Implemented |
| 12 | UNIMPLEMENTED | Implemented |
| 13 | INTERNAL | Implemented |
| 14 | UNAVAILABLE | Implemented |
| 15 | DATA_LOSS | Implemented |
| 16 | UNAUTHENTICATED | Implemented |

## Call Types

| Type | Status | Notes |
|------|--------|-------|
| Unary | Implemented | Single request, single response |
| Server Streaming | Implemented | Single request, stream of responses |
| Client Streaming | Implemented | Stream of requests, single response |
| Bidi Streaming | Implemented | Stream of requests, stream of responses |

## Compression

| Encoding | Status | Notes |
|----------|--------|-------|
| identity | Implemented | No compression (default) |
| gzip | Implemented | Using java.util.zip.GZIPOutputStream/GZIPInputStream |
| deflate | Implemented | Using java.util.zip.Deflater/Inflater |
| snappy | Not supported | |
| zstd | Not supported | |

## Server Features

| Feature | Status | Notes |
|---------|--------|-------|
| Service registry | Implemented | By fully qualified name |
| Method dispatch | Implemented | All four call types |
| Server interceptors | Implemented | Chain pattern |
| Error handling | Implemented | StatusException mapped to gRPC status |
| Response metadata | Implemented | Headers and trailers |

## Client Features

| Feature | Status | Notes |
|---------|--------|-------|
| Channel abstraction | Implemented | Loopback mode for testing |
| Dynamic stub | Implemented | Method resolution by name |
| Client interceptors | Implemented | Chain pattern |
| Call options | Implemented | Deadline, compression, metadata |
| Request encoding | Implemented | With optional compression |
| Response decoding | Implemented | With decompression |
| Cancellation | Implemented | Via cancel() |

## Not Implemented

- .proto file compilation / code generation
- gRPC-web protocol
- gRPC over HTTP/3 (QUIC)
- xDS load balancing
- Health checking protocol (grpc.health.v1)
- Reflection service (grpc.reflection.v1)
- Channelz
- Client-side load balancing
- Name resolution / service discovery
- Retry policy
- Hedging
- Wait-for-ready semantics
- Binary logging
- OpenTelemetry / OpenCensus tracing integration
