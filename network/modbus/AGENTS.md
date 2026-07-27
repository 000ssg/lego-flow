# Modbus Module — Development Guide

## Module Purpose

The `modbus` module implements Modbus TCP (MBAP/TCP) for industrial device communication. It provides both client and server implementations, built on the `service` module for TCP transport and `blocks` for data processing primitives.

## Key Interfaces

- `ModbusClient` — high-level client with typed methods for all 9 function codes
- `ModbusConnection` — low-level TCP connection with frame send/receive and transaction ID tracking
- `ModbusServer` — TCP server using virtual threads, one thread per connection
- `RequestHandler` — processes request PDUs against DeviceMemory, returns response PDUs
- `DeviceMemory` — thread-safe in-memory store for all four Modbus data tables
- `ModbusCodec` — encodes/decodes Modbus TCP frames (MBAP header + PDU) over streams
- `MbapHeader` — 7-byte MBAP header record (transaction ID, protocol ID, length, unit ID)
- `ModbusFrame` — complete frame record (MBAP header + PDU) with defensive copy semantics
- `FunctionCode` — enum of all 9 supported Modbus function codes with exception code support

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | Wire protocol: MBAP header, frame, codec, function codes, exception handling |
| `client` | Client implementation: ModbusClient (high-level API), ModbusConnection (TCP socket) |
| `server` | Server implementation: ModbusServer (virtual threads), RequestHandler, DeviceMemory |
| `data` | Data type records: Coil, DiscreteInput, Register, InputRegister with validation |

## Modbus-Specific Coding Conventions

### Function Codes (all 9)
- READ_COILS (0x01), READ_DISCRETE_INPUTS (0x02), READ_HOLDING_REGISTERS (0x03), READ_INPUT_REGISTERS (0x04)
- WRITE_SINGLE_COIL (0x05), WRITE_SINGLE_REGISTER (0x06)
- WRITE_MULTIPLE_COILS (0x0F), WRITE_MULTIPLE_REGISTERS (0x10)
- READ_WRITE_MULTIPLE_REGISTERS (0x17)

### Data Model (Four Tables)
- **Coils** (00001-09999) — single-bit, read/write (FC 01, 05, 15)
- **Discrete Inputs** (10001-19999) — single-bit, read-only (FC 02)
- **Holding Registers** (40001-49999) — 16-bit, read/write (FC 03, 06, 16, 23)
- **Input Registers** (30001-39999) — 16-bit, read-only (FC 04)

### Exception Codes (6 standard)
- ILLEGAL_FUNCTION (01), ILLEGAL_DATA_ADDRESS (02), ILLEGAL_DATA_VALUE (03)
- SERVER_DEVICE_FAILURE (04), ACKNOWLEDGE (05), SERVER_DEVICE_BUSY (06)

### MBAP Header Structure
- Transaction ID (2 bytes) — matches request to response
- Protocol ID (2 bytes) — always 0x0000 for Modbus TCP
- Length (2 bytes) — remaining byte count (Unit ID + PDU)
- Unit ID (1 byte) — identifies the target device (0-255)

### Wire Format
- All multi-byte values are big-endian (network byte order)
- Coil ON = 0xFF00, OFF = 0x0000 for single coil writes
- Boolean packing: LSB-first within each byte for coil/discrete read responses

## Thread Safety Model

- `DeviceMemory` uses `ReentrantReadWriteLock` for concurrent access
- `ModbusServer` spawns one virtual thread per accepted connection
- `ModbusConnection` uses `AtomicInteger` for transaction ID generation
- Server acceptor loop runs on its own virtual thread

## Testing Practices

- Unit tests for MBAP header: encode/decode round-trip, boundary values, validation
- Unit tests for codec: all 9 function code PDU builders, boolean pack/unpack, register unpack
- Frame tests: creation, exception detection, defensive copy, equality
- Function code tests: lookup, exception codes, round-trip
- Exception code tests: all 6 standard codes, lookup by value
- Data type tests: Coil, DiscreteInput, Register, InputRegister with validation
- All tests use loopback transport (no external devices required)
- Test count: 57

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
