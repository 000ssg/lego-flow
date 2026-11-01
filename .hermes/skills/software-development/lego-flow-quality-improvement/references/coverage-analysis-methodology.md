# Coverage Analysis Methodology

## JaCoCo Report Generation

### Maven
```bash
mvn test -P jacoco-coverage
mvn jacoco:report
```

### Gradle
```bash
./gradlew jacocoAggregateReport --no-daemon
```

## Coverage Metrics

| Metric | Description | Target |
|--------|-------------|--------|
| Instructions | Percentage of bytecode instructions executed | ≥90% |
| Branches | Percentage of conditional branches executed | ≥90% |
| Methods | Number of methods called | Track |
| Classes | Number of classes loaded | Track |

## Identifying Low-Coverage Modules

### Quick Check
```bash
# Find modules with <90% instruction coverage
for f in build/jacoco/aggregate/html/Lego_Flow/ssg.legoflow.*/index.html; do
  if [ -f "$f" ]; then
    name=$(basename $(dirname $f));
    pct=$(grep -oE '[0-9]+\.[0-9]+%' "$f" 2>/dev/null | head -1 | grep -oE '[0-9.]+');
    if [ -n "$pct" ] && [ $(echo "$pct < 90" | bc -l 2>/dev/null || echo "0") -eq 1 ]; then
      echo "$name: $pct%"
    fi
  fi
done | sort -t: -k2 -rn
```

### Detailed Analysis
```bash
# Get full coverage table
grep -E "ctr2" build/jacoco/aggregate/html/Lego_Flow/ssg.legoflow.acl/index.html
```

## Coverage by Module Type

| Module Type | Expected Coverage | Notes |
|-------------|------------------|-------|
| Core protocol | ≥90% | Critical path |
| Service layer | ≥85% | Business logic |
| Transport | ≥80% | I/O handling |
| Demo | Low | Example code |
| Benchmark | N/A | Not tested |
| ACL | 0% | Known gap — no tests |

## Increasing Coverage

### Strategies
1. **Unit tests for public APIs** — Test all public methods
2. **Edge cases** — Test null, empty, boundary conditions
3. **Error paths** — Test exception handling
4. **Integration tests** — Test component interaction

### Anti-Patterns
- Don't test getters/setters unless they have logic
- Don't test private methods directly
- Don't test framework code (JUnit, AssertJ, etc.)

## Reporting

Include in WIP.md:
```markdown
## Phase 4: Coverage Gap Analysis (P2)

**Status**: ✅ COMPLETE

**Current Coverage**:
- Overall: 60% instructions, 43% branches
- 408 packages analyzed
- 2,904 classes, 20,777 methods

**Modules Below 90%** (selected):
| Module | Instructions | Branches |
|--------|-------------|----------|
| ssg.legoflow.acl | 0% | 0% |
| ssg.legoflow.blocks | 85% | 54% |
| ssg.legoflow.service | 91% | 50% |
```