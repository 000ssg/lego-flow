# VT400 Terminal — Architecture

## Overview

Extends VT200 with workstation capabilities: extended SGR color codes and 2-window support.

## Inheritance

```
VT100Terminal → VT200Terminal → VT400Terminal
```

## Window Model

The VT400 supports 2 windows. Window selection via CSI n t switches the active window. Reset defaults to window 1.

---

**Last Updated**: 2026-08-17
