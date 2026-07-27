# Blocks Module — Code Overview

> Cross-references: [README](README.md) | [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md)
> Parent: [Project CODE_OVERVIEW](../doc/CODE_OVERVIEW.md)

---

## Module Goals

The `blocks` module is the **foundational layer** of Lego Flow. It defines five orthogonal abstractions that every other module (42 total) builds upon. It has **zero runtime dependencies** beyond the JDK — not even SLF4J is required (the `Context` interface returns a `Logger`, but the implementation uses `LoggerFactory` only in `DefaultContext`).

---

## Source Structure

```
blocks/src/main/java/ssg/legoflow/blocks/
├── DataProcessor.java           — interface: consume/produce/accept/submit + state + filters + stats
├── AbstractDataProcessor.java   — template-method base: wires filter chains, stats, state machine
├── DataFilter.java              — interface: filter(ctx, T...) + state + stats
├── AbstractDataFilter.java      — template-method base: records in/out stats, delegates to doFilter()
├── Context.java                 — interface: logger, statistics, error handler, attributes
├── DefaultContext.java          — default implementation using SLF4J + ConcurrentHashMap attributes
├── ProcessorState.java          — enum IDLE/CONNECTING/READY/PAUSED/FAILED/STOPPED + transition table
├── ProcessorStatistics.java     — ConcurrentHashMap<String,AtomicLong> per-type counters + snapshot
├── StateListener.java           — functional interface: onStateChanged(old, new)
├── exceptions/
│   ├── ProcessorException.java  — root checked-style runtime exception
│   ├── StateTransitionException — invalid transition (from, to, message)
│   └── FilterRejectedException  — filter explicitly rejected data
└── demo/
    ├── PassthroughProcessor.java — I=O, identity conversion; used heavily in tests
    ├── StringToIntProcessor.java — String→Integer, skips unparseable; demonstrates error tolerance
    ├── LoggingFilter.java        — logs items, passes all through; demonstrates side-effect filter
    ├── TransformFilter.java      — maps each item via a Function<T,T>
    ├── ValidationFilter.java     — keeps items matching a Predicate<T>, drops the rest
    └── BidirectionalPipe.java    — closes two processors together; demo of resource management
```

**Note on demo/ placement:** These demo classes live in `src/main/java` (not `src/test/java`), which means they ship in the production artifact. See [inconsistency #6 in CODE_OVERVIEW](../doc/CODE_OVERVIEW.md#6-demo-only-classes-in-main-source-tree).

---

## Key Abstractions

### DataProcessor<I,O>

The central abstraction. Models a **bidirectional data processing unit**:

```
Remote side                    Local side
    ↓ consume(I)         accept(O) ↑
  DF<I> → convert(I→O) → DF<O> → accept
  DF<I> ← convert(O→I) ← DF<O> ← submit
    ↑ produce(I)         submit(O) ↓
```

- `consume(ctx, I...)` — data arrives from the remote side; run through input filters, convert to O, run through output filters, call `accept()`.
- `produce(ctx, I...)` — send data to the remote side (e.g., write to a socket). Subclasses implement the actual I/O.
- `accept(ctx, O...)` — locally receive converted O data. Subclasses override to process results.
- `submit(ctx, O...)` — local code sends O data outward; reverse of consume.

**Design decision:** The varargs `I...` signature allows batching multiple items per call without needing a `List` wrapper. The tradeoff is a temporary array allocation per call; acceptable at the data-processing layer.

### AbstractDataProcessor<I,O>

Template method implementation. Subclasses implement:
- `O[] convertToOutput(Context ctx, I... input)` — I→O conversion (e.g., bytes→message)
- `I[] convertToInput(Context ctx, O... output)` — O→I conversion (e.g., message→bytes)

State transitions (`transitionTo`) are `protected`, allowing subclasses (e.g., protocol services) to manage their own lifecycle.

`close()` is idempotent: calling it on an already-STOPPED processor is a no-op (no listener notification on second call).

### DataFilter<T>

Positioned filter that can inspect, transform, or reject data. `AbstractDataFilter` handles statistics (`recordIn`/`recordOut`) around `doFilter()`. Filters are chained: each filter receives the output of the previous.

If any filter returns an empty/null array, the chain short-circuits — no further filters run and the processor's conversion/accept is skipped.

### ProcessorState

6-state machine with a compile-time-defined transition table (`Map<ProcessorState, EnumSet<ProcessorState>>`):

| From | To |
|---|---|
| IDLE | CONNECTING, READY, STOPPED |
| CONNECTING | READY, FAILED, STOPPED |
| READY | PAUSED, FAILED, STOPPED |
| PAUSED | READY, FAILED, STOPPED |
| FAILED | CONNECTING, READY, STOPPED |
| STOPPED | *(none — terminal)* |

**Design decision:** IDLE can go directly to READY (skipping CONNECTING). This supports simple in-process processors that don't need a connect phase.

### ProcessorStatistics

ConcurrentHashMap-based counters keyed by `Class.getName()`. Supports heterogeneous pipelines (different I and O types tracked separately). The `snapshot()` method returns an immutable copy for safe external inspection.

---

## Test Coverage

Tests are in `blocks/src/test/java/ssg/legoflow/blocks/`:

| Test class | What it covers |
|---|---|
| `AbstractDataProcessorTest` | consume/produce/accept/submit flow, filter chains, statistics, state listeners, close idempotency, invalid transition throws |
| `DataFilterTest` | filter/transform/validate, statistics, state lifecycle, null/empty input, invalid transition throws |
| `ProcessorStateTest` | all valid and invalid transitions for each state |
| `ProcessorStatisticsTest` | recordIn/recordOut, per-type isolation, snapshot, reset |
| `ContextTest` | getAttribute/setAttribute, null removes, handleError |
| `exceptions/ExceptionsTest` | all three exception classes: message, cause, hierarchy, fields |
| `demo/BidirectionalPipeDemoTest` | pipe close semantics, left→right flow |
| `demo/FilterChainDemoTest` | multi-filter chains |
| `demo/SimplePassthroughDemoTest` | basic consume→accept |
| `demo/StatefulProcessorDemoTest` | state transition sequence |
| `demo/StatisticsAggregationDemoTest` | aggregate statistics across processors |
| `demo/StringToIntConverterDemoTest` | string→int conversion, invalid input tolerance |

**Total: 84 tests** (as of 2026-07-11, increased from 66 in this session).

### Gaps addressed in this session

- Added `exceptions/ExceptionsTest` covering `ProcessorException`, `FilterRejectedException`, `StateTransitionException` (message, cause, hierarchy, getFrom/getTo).
- Added `testCloseIsIdempotent` — verifies second `close()` does not fire listener again.
- Added `testInvalidStateTransitionThrows` — verifies `StateTransitionException` on illegal transition.
- Added `testStateListenerReceivesOldAndNewState` — verifies both `old` and `new` states are passed.
- Added `testConsumeWithAllFiltersRejecting` — verifies accept is not called when all input is filtered out.
- Added `testProduceRecordsStats` — verifies produce records output statistics.
- Added `testSubmitWithAllOutputFiltersRejecting` — verifies produce is not called when output filters reject all.
- Added `testFilterCloseIsIdempotent` — mirrors processor idempotency for filters.
- Added `testFilterInvalidTransitionThrows` — invalid transition on AbstractDataFilter.
- Added `testFilterWithNullInput` — null input passes through unchanged.

---

## Inconsistencies and Proposals

See [project-level CODE_OVERVIEW.md](../doc/CODE_OVERVIEW.md) section "Inconsistencies, Inefficiencies, and Ambiguities Found" for items 1, 3, 4, 5, 6, 7.
