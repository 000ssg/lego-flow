# VT500 Terminal — Architecture

## Overview

Extends VT400 with DEC character set selection via SO/SI and G0/G1 charset registers.

## Inheritance

```
VT100Terminal → VT200Terminal → VT400Terminal → VT500Terminal
```

## Character Set Model

```mermaid
graph TD
    G0["G0 Register"]
    G1["G1 Register"]
    A["Active Charset"]
    
    G0 --> A
    G1 --> A
    
    SO["SO (0x0E) → select G0"]
    SI["SI (0x0F) → select G1"]
    
    SO --> G0
    SI --> G1
```

---

**Last Updated**: 2026-08-17
