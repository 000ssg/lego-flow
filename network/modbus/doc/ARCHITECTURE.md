# Modbus Module — Architecture

This document describes the architectural decisions for the Modbus module.

---

## Protocol Overview

Modbus is a serial communication protocol originally designed by Modicon (now Schneider Electric) in 1979 for use with programmable logic controllers (PLCs). Modbus TCP encapsulates the Modbus application protocol inside TCP/IP, replacing the serial link layer with MBAP (Modbus Application Protocol) headers. The Lego Flow implementation provides both client and server over TCP transport.

## Layered Architecture

```mermaid
graph TD
    L1["ModbusClient / ModbusServer<br/>(connection management, API surface)"]
    L2["RequestHandler<br/>(PDU dispatch, function code routing,<br/>exception response generation)"]
    L3["DeviceMemory<br/>(four data tables: coils, discrete inputs,<br/>holding registers, input registers)"]
    L4["ModbusCodec<br/>(frame encode/decode, PDU builders,<br/>boolean pack/unpack, register parsing)"]
    L5["MbapHeader + ModbusFrame<br/>(7-byte MBAP header, PDU container,<br/>validation, defensive copy)"]
    L6["TCP Transport<br/>(java.net.Socket, java.net.ServerSocket,<br/>virtual threads)"]

    L1 --> L2 --> L3
    L1 --> L4 --> L5 --> L6
```

## MBAP Header Format

The MBAP (Modbus Application Protocol) header replaces the serial Modbus framing for TCP transport:

```mermaid
graph LR
    subgraph "MBAP Header (7 bytes)"
        TID["Transaction ID<br/>2 bytes"]
        PID["Protocol ID<br/>2 bytes<br/>(always 0x0000)"]
        LEN["Length<br/>2 bytes"]
        UID["Unit ID<br/>1 byte"]
    end
    subgraph "PDU (variable)"
        FC["Function Code<br/>1 byte"]
        DATA["Data<br/>N bytes"]
    end
    TID --> PID --> LEN --> UID --> FC --> DATA
```

- **Transaction ID**: correlates request/response pairs (0-65535, auto-incremented by client)
- **Protocol ID**: always 0x0000 for Modbus TCP
- **Length**: byte count of Unit ID + PDU
- **Unit ID**: target device identifier (0-255), used for gateway routing

## Request/Response Flow

```mermaid
sequenceDiagram
    participant Client as ModbusClient
    participant Conn as ModbusConnection
    participant Server as ModbusServer
    participant Handler as RequestHandler
    participant Mem as DeviceMemory

    Client->>Conn: readHoldingRegisters(addr, qty)
    Conn->>Conn: Build PDU (FC 0x03)
    Conn->>Conn: Create MBAP header (txId++)
    Conn->>Server: TCP: MBAP + PDU
    Server->>Handler: handle(pdu)
    Handler->>Mem: readHoldingRegisters(addr, qty)
    Mem-->>Handler: int[] values
    Handler-->>Server: response PDU
    Server-->>Conn: TCP: MBAP + response PDU
    Conn->>Conn: Check for exception response
    Conn-->>Client: int[] values
```

## Exception Response Flow

```mermaid
sequenceDiagram
    participant Client as ModbusClient
    participant Server as ModbusServer
    participant Handler as RequestHandler

    Client->>Server: Request (FC 0x03, invalid address)
    Server->>Handler: handle(pdu)
    Handler->>Handler: Address validation fails
    Handler-->>Server: Exception PDU (FC 0x83 + code 0x02)
    Server-->>Client: Exception response
    Client->>Client: Throw ModbusException(ILLEGAL_DATA_ADDRESS)
```

When the function code in the response has bit 7 set (code | 0x80), it indicates an exception. The next byte carries the exception code (1-6).

## Server Architecture

```mermaid
graph TD
    SS["ServerSocket<br/>(bind to port)"] --> ACC["Acceptor Thread<br/>(virtual thread)"]
    ACC -->|"accept()"| VT1["Client Thread 1<br/>(virtual thread)"]
    ACC -->|"accept()"| VT2["Client Thread 2<br/>(virtual thread)"]
    ACC -->|"accept()"| VTN["Client Thread N<br/>(virtual thread)"]
    VT1 --> RH["RequestHandler"]
    VT2 --> RH
    VTN --> RH
    RH --> DM["DeviceMemory<br/>(ReadWriteLock)"]
```

- **Virtual threads**: each client connection runs in its own virtual thread (`Thread.ofVirtual()`)
- **Acceptor loop**: dedicated virtual thread accepts incoming connections
- **Shared state**: all connections share a single `DeviceMemory` instance protected by `ReentrantReadWriteLock`
- **Connection lifecycle**: read request frame, dispatch to handler, write response frame, repeat until disconnect

## Client Architecture

- **ModbusClient**: high-level API with typed methods (`readCoils`, `writeMultipleRegisters`, etc.)
- **ModbusConnection**: low-level TCP socket management, frame serialization, transaction ID tracking
- Transaction IDs auto-increment per connection using `AtomicInteger` (wraps at 0xFFFF)
- Exception responses are detected by checking bit 7 of the function code and thrown as `ModbusException`

## Data Model

```mermaid
graph TD
    subgraph "DeviceMemory (65536 addresses per table)"
        C["Coils<br/>boolean[65536]<br/>R/W — FC 01, 05, 15"]
        DI["Discrete Inputs<br/>boolean[65536]<br/>R/O — FC 02"]
        HR["Holding Registers<br/>int[65536]<br/>R/W — FC 03, 06, 16, 23"]
        IR["Input Registers<br/>int[65536]<br/>R/O — FC 04"]
    end
```

- **Coils**: single-bit outputs, read/write
- **Discrete Inputs**: single-bit inputs, read-only (writable via `setDiscreteInput` for simulation)
- **Holding Registers**: 16-bit values, read/write
- **Input Registers**: 16-bit values, read-only (writable via `setInputRegister` for simulation)

## Thread Safety Model

| Component | Mechanism | Details |
|-----------|-----------|---------|
| `DeviceMemory` | `ReentrantReadWriteLock` | Read operations acquire read lock; write operations acquire write lock. Multiple concurrent readers allowed. |
| `ModbusServer` | Virtual threads | One virtual thread per connection. Acceptor on its own virtual thread. `volatile boolean running` for shutdown. |
| `ModbusConnection` | `AtomicInteger` | Thread-safe transaction ID generation. Socket I/O is single-threaded per connection. |

## Integration with Lego Flow

| Lego Flow Module | Usage in Modbus |
|------------------|-----------------|
| `blocks` | DP/DF data processing primitives available for building pipelines around Modbus data |
| `service` | TCP transport abstractions, lifecycle management, virtual thread integration |

## Design Decisions

1. **Stream-based codec**: `ModbusCodec` reads/writes directly from `InputStream`/`OutputStream` rather than using `ByteBuffer` accumulation, since Modbus TCP has a fixed-size header (7 bytes) that declares the exact PDU length, making length-prefixed reads straightforward.

2. **Records for protocol types**: `MbapHeader`, `ModbusFrame`, `Coil`, `Register`, `DiscreteInput`, `InputRegister` are all Java records for immutability and compact representation.

3. **Defensive copy in ModbusFrame**: PDU byte arrays are cloned on construction and on access to prevent mutation of internal state.

4. **Utility-class codec**: `ModbusCodec` is a static utility class (private constructor) since it carries no state — all encode/decode operations are stateless.

5. **Virtual threads over NIO**: the server uses blocking I/O with virtual threads rather than NIO selectors, matching the Lego Flow convention for protocol servers.

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../../doc/ARCHITECTURE.md) | [Root README](../../../README.md)

---

**Last Updated**: 2026-07-06
