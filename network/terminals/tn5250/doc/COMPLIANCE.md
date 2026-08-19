# TN5250 Terminal — Compliance

## RFC 1662 — TN5250 Protocol

This module implements the IBM 5250 terminal protocol over TN5250, as
defined in RFC 1662 (TN5250 Protocol) and the IBM 5250 Information Entry
Protocol specification.

### Screen Model (RFC 1662 §3)

| Feature | Standard | Status |
|---------|----------|--------|
| 24×80 grid | Standard | ✅ Implemented |
| 52×80 grid | Lettermode | ✅ Implemented |
| 32-byte keyboard area | Required | ✅ Implemented |
| Field-structured cells | Required | ✅ Implemented |

### Field Attributes (RFC 1662 §3.1)

The 5250 field attributes are encoded in a single byte with three bits:

| Bit | Attribute | Value | Status |
|-----|-----------|-------|--------|
| 0 | Emphasis | 0x01 | ✅ Implemented |
| 1 | Automatic Skip | 0x02 | ✅ Implemented |
| 2 | Blank (Protected) | 0x04 | ✅ Implemented |
| 3–7 | Reserved | 0x00 | ✅ Handled (ignored) |

| Attribute Byte | Meaning | Status |
|----------------|---------|--------|
| 0x00 | Normal (editable) | ✅ Implemented |
| 0x01 | Emphasized | ✅ Implemented |
| 0x02 | Auto-skip | ✅ Implemented |
| 0x03 | Emphasized + Auto-skip | ✅ Implemented |
| 0x04 | Blank (protected) | ✅ Implemented |
| 0x05 | Emphasized + Blank | ✅ Implemented |
| 0x07 | Full (all attributes) | ✅ Implemented |

### Data Stream Format

| Component | Encoding | Status |
|-----------|----------|--------|
| Field length | 1–2 bytes, variable | ✅ Implemented |
| Field data | Variable length | ✅ Implemented |
| Field attribute byte | 1 byte (3 bits) | ✅ Implemented |

### Keyboard Area

| Key Type | Codes | Status |
|----------|-------|--------|
| PF keys | 0x91–0x9C | ✅ Implemented |
| PA keys | 0x9D–0x9F | ✅ Implemented |
| Cursor keys | 0x88–0x8B | ✅ Implemented |
| Edit keys (Clear, Erase, etc.) | 0x82–0x87 | ✅ Implemented |
| Navigation keys | 0x91–0x97 | ✅ Implemented |
| Printable characters | 0x40–0x7E | ✅ Implemented |

### Control Functions

| Function | Byte | Status |
|----------|------|--------|
| Attention | 0x88 | ✅ Implemented |
| Help | 0x97 | ✅ Implemented |
| Copy | 0x8C | ✅ Implemented |
| Paste | 0x8D | ✅ Implemented |

## Limitations and Rationale

### Limitation: 5250 Escape Sequences
5250 uses escape sequences for many screen operations (e.g., cursor
movement, field protection). These escape sequences are implemented
in the data stream parsing layer, not directly in the terminal
emulator screen model.

**Reason:** The 5250 escape sequences are processed by the TN5250
gateway which translates them into structured field records before
the terminal emulator sees them. The terminal emulator handles
field-level operations (writeChars, setFieldAttrs, etc.) but does
not parse raw 5250 escape sequences — that is a gateway concern.

### Limitation: Color and Background
IBM 5250 color displays support background colors for some fields.
This module does not implement background color attributes.

**Reason:** Standard 5250 color support requires hardware-specific
color mappings that vary by display model. The core 5250 field model
(emphasis, auto-skip, blank) is fully implemented. Color support can
be added as an extension if specific host requirements demand it.

### Limitation: Extended 5250 Features
Features like 5250 Unicode support, alternate screens, and 132-column
modes are not implemented.

**Reason:** The core 5250 protocol (24×80, standard fields) is fully
implemented. Extended features are enhancements beyond the base RFC 1662
specification and can be added as needed for specific IBM i host systems.

### Limitation: Change Detection
Like TN3270, TN5250 uses change detection markers for efficient
screen updates. This module does not implement CD tracking.

**Reason:** CD tracking is a gateway-layer responsibility. The terminal
emulator receives already-structured field data from the gateway and
does not need to track changes itself.

## Reference Implementations

| Implementation | URL | Notes |
|---------------|-----|-------|
| Open 5250 | Part of open3270 project | Java-based 5250 client |
| IBM 5250 Emulation | IBM internal spec SA22-7205 | Official IBM 5250 protocol |
| RFC 1662 | https://tools.ietf.org/html/rfc1662 | TN5250 Protocol |
