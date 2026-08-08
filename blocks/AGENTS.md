# blocks — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The **blocks** module is the core data processing framework for Lego Flow. It defines the fundamental abstractions: `DataProcessor<I,O>` (DP), `DataFilter<T>` (DF), `Context`, `ProcessorState`, and `ProcessorStatistics`.

## Key Interfaces

### DP<I,O> — DataProcessor
- `consume(ctx, I...)` — receive I from remote → DF<I> → convert I→O → DF<O> → accept(O)
- `produce(ctx, I...)` — send I remotely
- `accept(ctx, O...)` — locally receive O
- `submit(ctx, O...)` — locally submit O → DF<O> → convert O→I → DF<I> → produce(I)

### DF<T> — DataFilter
- Positioned at boundaries: DF<I> wraps remote side, DF<O> wraps local side
- Can transform, validate, or reject data

### Context
- Provides logging (SLF4J), statistics, error handling, arbitrary attributes

### ProcessorState
- IDLE → CONNECTING → READY → PAUSED → FAILED → STOPPED

## Dependencies
- None (this is the base module)
