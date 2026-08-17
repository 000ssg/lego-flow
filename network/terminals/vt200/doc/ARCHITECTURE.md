# VT200 Terminal — Architecture

## Overview

The VT200 extends VT100 with video reverse mode (SGR 52/55). It inherits the full VT100 protocol and adds SGR codes specific to the VT200 hardware.

## Inheritance

```
VT100Terminal → VT200Terminal
```

The VT200 intercepts SGR parameter 52 (video reverse on) and 55 (video reverse off) before delegating to VT100.

---

**Last Updated**: 2026-08-17
