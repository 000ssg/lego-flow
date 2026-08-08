
# Lego Flow Modbus Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-57-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

Modbus TCP protocol module for the Lego Flow framework, providing industrial device communication with client and server implementations.

## Overview

This module implements the Modbus TCP protocol (Modbus Application Protocol over TCP/IP), enabling Java applications to communicate with industrial PLCs, sensors, and actuators. The architecture layers protocol handling on top of the framework's service and blocks abstractions:

```
ModbusClient / ModbusServer (application layer)
  -> RequestHandler + DeviceMemory (request processing, data storage)
    -> ModbusCodec (frame encode/decode, PDU builders)
      -> MbapHeader + ModbusFrame (wire format records)
        -> TCP Transport (java.net.Socket, virtual threads)
```

## Features

- **Modbus TCP** — full MBAP/TCP implementation per the Modbus TCP specification
- **9 Function Codes** — all standard read/write operations for coils, discrete inputs, holding registers, and input registers
- **Client** — high-level typed API for all function codes, automatic transaction ID tracking
- **Server** — virtual-thread-per-connection architecture, configurable device memory
- **Device Memory** — thread-safe in-memory data store for all four Modbus data tables (65536 addresses each)
- **Exception Handling** — 6 standard Modbus exception codes with structured error responses
- **Data Types** — type-safe records for Coil, DiscreteInput, Register, InputRegister
- **Wire Codec** — stream-oriented encode/decode with MBAP header validation

## Quick Start

### Start a server

```java
var memory = new DeviceMemory();
memory.writeHoldingRegister(0, 12345);
memory.writeCoil(0, true);

var server = new ModbusServer(502, memory);
server.start();
```

### Connect a client and read registers

```java
try (var client = new ModbusClient("localhost", 502)) {
    int[] registers = client.readHoldingRegisters(0, 10);
    boolean[] coils = client.readCoils(0, 8);
    
    client.writeSingleRegister(100, 42);
    client.writeSingleCoil(50, true);
}
```

### Write multiple values

```java
try (var client = new ModbusClient("localhost", 502, 1)) {
    client.writeMultipleRegisters(0, new int[]{100, 200, 300});
    client.writeMultipleCoils(0, new boolean[]{true, false, true, true});
    
    // Read and write in a single transaction (FC 23)
    int[] result = client.readWriteMultipleRegisters(
        0, 5,           // read 5 registers from address 0
        10, new int[]{1, 2}  // write 2 registers at address 10
    );
}
```

## Supported Function Codes

| Code | Name | Operation |
|------|------|-----------|
| 0x01 | Read Coils | Read 1-2000 contiguous coils |
| 0x02 | Read Discrete Inputs | Read 1-2000 contiguous discrete inputs |
| 0x03 | Read Holding Registers | Read 1-125 contiguous holding registers |
| 0x04 | Read Input Registers | Read 1-125 contiguous input registers |
| 0x05 | Write Single Coil | Write one coil (ON/OFF) |
| 0x06 | Write Single Register | Write one holding register |
| 0x0F | Write Multiple Coils | Write 1-1968 contiguous coils |
| 0x10 | Write Multiple Registers | Write 1-123 contiguous holding registers |
| 0x17 | Read/Write Multiple Registers | Atomic read + write in one transaction |

## Package Structure

```
ssg.legoflow.network.modbus/
├── protocol/          — Wire protocol: MBAP header, frame, codec, function codes, exceptions
├── client/            — Client: ModbusClient (high-level), ModbusConnection (TCP socket)
├── server/            — Server: ModbusServer (virtual threads), RequestHandler, DeviceMemory
└── data/              — Data type records: Coil, DiscreteInput, Register, InputRegister
```

## Dependencies

This module depends on:
- `lego-flow-blocks` — DP/DF data processing primitives
- `lego-flow-service` — TCP transport, lifecycle management, virtual threads

---

**Part of the [Lego Flow](../../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
