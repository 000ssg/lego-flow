# Modbus Module — Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 57
- **Dependencies**: blocks (DP/DF), service (TCP transport)
- **Standards**: Modbus Application Protocol Specification V1.1b3, Modbus TCP/IP Implementation Guide V1.0b

---

## Requirements

### MBAP Header (Protocol Layer)
1. Encode and decode the 7-byte MBAP header: Transaction ID (2 bytes), Protocol ID (2 bytes), Length (2 bytes), Unit ID (1 byte)
2. Validate Protocol ID is always 0x0000 for Modbus TCP
3. Validate field ranges: Transaction ID 0-65535, Length 0-65535, Unit ID 0-255
4. Provide factory method for request headers that auto-calculates length from PDU size

### Modbus Frame
1. Combine MBAP header with PDU (function code + data) into a complete frame
2. Defensive copy of PDU bytes on construction and access to prevent mutation
3. Extract function code from first PDU byte
4. Detect exception responses by checking bit 7 of the function code
5. Extract data portion (PDU bytes after function code)

### Codec
1. Encode complete Modbus frames to byte arrays (MBAP header + PDU)
2. Write frames to OutputStream with flush
3. Read frames from InputStream with length-prefixed parsing
4. Build request PDUs for all 9 function codes with correct binary format
5. Pack boolean arrays into bytes (LSB-first) for coil writes
6. Unpack boolean bytes back to arrays for coil/discrete input reads
7. Unpack register response bytes into unsigned 16-bit integer arrays
8. Build exception response PDUs with function code + 0x80 and exception code
9. Validate quantity ranges per function code (coils: 1-2000, registers: 1-125, etc.)

### Function Codes
1. Define all 9 standard function codes as an enum with numeric values
2. Support lookup by numeric code value
3. Generate exception codes (original code | 0x80)
4. Detect exception responses by checking bit 7
5. Recover original function code from exception code (code & 0x7F)

### Exception Handling
1. Define 6 standard Modbus exception codes: ILLEGAL_FUNCTION (01), ILLEGAL_DATA_ADDRESS (02), ILLEGAL_DATA_VALUE (03), SERVER_DEVICE_FAILURE (04), ACKNOWLEDGE (05), SERVER_DEVICE_BUSY (06)
2. Support exception code lookup by numeric value
3. ModbusException carries optional ExceptionCode for structured error reporting
4. Support chained exceptions with cause

### Client
1. High-level typed methods for all 9 function codes
2. Automatic MBAP header construction with incrementing transaction IDs
3. Response parsing: extract byte count, unpack coil/register data
4. Exception response detection: throw ModbusException with structured exception code
5. Connection state tracking (isConnected)
6. AutoCloseable for resource cleanup
7. Configurable unit ID (default: 1)

### Server
1. Accept TCP connections on configurable port with SO_REUSEADDR
2. Virtual thread per connection (Thread.ofVirtual)
3. Acceptor loop on dedicated virtual thread
4. Process requests in a loop until client disconnects or server stops
5. Dispatch PDUs to RequestHandler, return response PDUs
6. Match response MBAP transaction ID and unit ID to request
7. Graceful shutdown via volatile running flag and ServerSocket close
8. AutoCloseable for resource cleanup

### Request Handler
1. Route requests by function code to appropriate handler method
2. Handle all 9 function codes against DeviceMemory
3. Return exception response for unknown function codes (ILLEGAL_FUNCTION)
4. Return ILLEGAL_DATA_ADDRESS for out-of-bounds address ranges
5. Return SERVER_DEVICE_FAILURE for unexpected errors
6. Validate single coil write value (must be 0xFF00 or 0x0000)
7. Echo request PDU for single writes (FC 05, 06) per specification
8. Return start address + quantity for multiple writes (FC 15, 16) per specification

### Device Memory
1. Four data tables: coils, discrete inputs, holding registers, input registers
2. Configurable address space size (default: 65536)
3. Thread-safe with ReentrantReadWriteLock (multiple concurrent readers)
4. Read operations: readCoils, readDiscreteInputs, readHoldingRegisters, readInputRegisters
5. Write operations: writeCoil, writeCoils, writeHoldingRegister, writeHoldingRegisters
6. Simulation helpers: setDiscreteInput, setInputRegister (for read-only tables)
7. Address range validation on all operations
8. 16-bit value masking (& 0xFFFF) on register writes

### Data Types
1. Coil record: address (0-65535) + boolean value, factory methods on/off
2. DiscreteInput record: address (0-65535) + boolean value
3. Register record: address (0-65535) + value (0-65535), factory method of
4. InputRegister record: address (0-65535) + value (0-65535), factory method of
5. All records validate ranges in compact constructors

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
