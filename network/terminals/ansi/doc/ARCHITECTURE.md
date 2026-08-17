# ANSI Terminal — Architecture

## Overview

The ANSI terminal implements the ANSI X3.64 standard subset of VT100, filtering out DEC private mode sequences (those starting with `ESC [ ?`).

## Inheritance

```
VT100Terminal → ANSITerminal → XTERMTerminal
```

## DEC Private Mode Filtering

The ANSI terminal overrides `handleCSI()` to check if intermediates equal `?`. If so, the sequence is silently ignored. This ensures strict ANSI X3.64 compliance while reusing VT100's standard CSI handling.

---

**Last Updated**: 2026-08-17
