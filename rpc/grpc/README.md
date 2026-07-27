# Lego Flow gRPC -- Google Remote Procedure Call Protocol

Pure JDK implementation of the gRPC protocol over HTTP/2 with runtime protobuf encoding.
No external dependencies, no `.proto` code generation -- all descriptors are built at runtime.

## Architecture

```
ssg.legoflow.rpc.grpc/
  protobuf/      -- Protobuf wire format (varint, zigzag, all wire types, nested/repeated/packed/map/oneof)
  transport/     -- gRPC framing over HTTP/2 (length-prefixed messages, status codes, headers, compression, timeouts)
  metadata/      -- Typed key-value metadata for headers and trailers
  common/        -- Shared types (MethodDescriptor, MethodType, StatusException)
  server/        -- gRPC server with service registry, interceptors, and call handlers
  client/        -- gRPC client with channel, dynamic stub, interceptors, and call options
  demo/          -- Demo services (Calculator, FileDownload, Upload, Chat)
```

## Call Types

| Type | Description | Example |
|------|-------------|---------|
| Unary | Single request, single response | Calculator.Add |
| Server Streaming | Single request, stream of responses | FileDownload.Download |
| Client Streaming | Stream of requests, single response | Upload.Upload |
| Bidi Streaming | Stream of requests and responses | Chat.Chat |

## Protobuf Wire Format

Implemented from scratch, supporting:

- Varint encoding (LEB128) for int32, int64, uint32, uint64, bool, enum
- ZigZag encoding for sint32, sint64
- Fixed-width 32-bit and 64-bit fields (fixed32, sfixed32, float, fixed64, sfixed64, double)
- Length-delimited fields (string, bytes, embedded messages)
- Repeated fields (both unpacked and packed encoding)
- Map fields (encoded as repeated key-value message entries)
- Oneof fields
- Nested messages

## gRPC over HTTP/2

- Method path format: `/package.ServiceName/MethodName`
- Content-Type: `application/grpc`, `application/grpc+proto`
- Request: HTTP/2 POST with gRPC headers, length-prefixed protobuf messages
- Response: HTTP/2 200 with length-prefixed messages, then trailers with `grpc-status` and `grpc-message`
- Message framing: 1-byte compressed flag + 4-byte big-endian length + protobuf bytes
- Compression: identity, gzip, deflate
- Timeout: `grpc-timeout` header in nS/nM/nH format
- All 17 gRPC status codes

## Usage

### Server

```java
var server = new GrpcServer();
CalculatorService.register(server);

// Process an incoming request
var result = server.processRequest("/demo.Calculator/Add", requestData, metadata);
```

### Client (loopback for testing)

```java
var channel = new GrpcChannel(server);
var stub = new GrpcStub(channel, CalculatorService.serviceDescriptor());
var response = stub.unaryCall("Add", new ProtoMessage().setDouble(1, 3.0).setDouble(2, 4.0));
double result = response.getDouble(1); // 7.0
```

### Runtime descriptors

```java
var descriptor = MessageDescriptor.builder("Person")
    .addField(FieldDescriptor.scalar(1, "name", FieldDescriptor.Type.STRING))
    .addField(FieldDescriptor.scalar(2, "age", FieldDescriptor.Type.INT32))
    .addField(FieldDescriptor.repeated(3, "tags", FieldDescriptor.Type.STRING, false))
    .build();

var msg = new ProtoMessage()
    .setString(1, "Alice")
    .setVarint(2, 30)
    .setRepeated(3, List.of(
        FieldValue.BytesValue.fromString("engineer"),
        FieldValue.BytesValue.fromString("java")));

byte[] encoded = ProtobufCodec.encode(msg, descriptor);
ProtoMessage decoded = ProtobufCodec.decode(encoded, descriptor);
```

## Known Limitations

- No `.proto` file compilation (runtime descriptors only)
- No gRPC-web, no gRPC over HTTP/3
- No xDS load balancing
- No health checking protocol (grpc.health.v1)
- No reflection service (grpc.reflection.v1)
- No channelz
- Remote (non-loopback) client calls construct HTTP/2 frames but require wire I/O integration for production use
