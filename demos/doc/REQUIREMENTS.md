# Demos Module — Requirements Evolution

## Module Timeline Overview

- **Start Date**: 2026-07-28 (Quick Wins commit moved demos from protocol modules)
- **Total Tests**: 0 (demo module — excluded from install/deploy lifecycle)
- **Purpose**: Protocol demonstration and example code, separated from production library sources

---

## Table of Contents

- [Module Timeline Overview](#module-timeline-overview)
- **Commits by date (newest → oldest)**
  - [Quick Wins Commit — Cleanup ephemeral files, configure Maven parallelism, consolidate AGENTS.md](#commit-quick-wins-2026-07-28)

---

## Quick Wins Commit — Cleanup Ephemeral Files, Configure Maven Parallelism, Consolidate AGENTS.md

### Original Request
> "Apply improvements from the proposal document including MT-2 (move demo code from main sources to test/demo module)."

### Reformulated Requirements

1. Move all demo packages from protocol modules into a dedicated demos module
2. Exclude demos module from Maven install/deploy lifecycle
3. Update imports in moved files to reflect new package paths
4. Consolidate AGENTS.md files across the project

### Final Design Decisions

- **Single demos module**: `lego-flow-demos` aggregates all demo code from protocol modules
- **Excluded from publish lifecycle**: maven-install-plugin and maven-deploy-plugin configured with `<skip>true</skip>`
- **No test scope**: Demos remain in main sources (not test) because they are reference implementations, not tests

### Implementation Details

- `demos/pom.xml`: Maven module excluded from install/deploy
- Demo classes moved from per-module demo packages into demos module
- Cross-references updated to point to new package structure

### Test Coverage

- **0 unit tests** (demo module — contains reference implementations, not test assertions)

---
