
# rpc — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The `rpc` module is a parent POM (packaging=pom) that groups all RPC and query protocol sub-modules under a single build hierarchy.

## Module Structure

```
rpc/                             <- parent POM (lego-flow-rpc)
  grpc/                          <- gRPC protocol (Google RPC)
  graphql/                       <- GraphQL query language
```

## Parent Chain

```
lego-flow (root)
  -> lego-flow-rpc (rpc/pom.xml)
      -> lego-flow-grpc (rpc/grpc/pom.xml)
      -> lego-flow-graphql (rpc/graphql/pom.xml)
```

## Test Counts

| Module | Test Files |
|--------|------------|
| grpc | 31 |
| graphql | 20 |

## Build Commands

```bash
# Build all RPC modules
mvn test -pl rpc/grpc,rpc/graphql -am

# Build single module
mvn test -pl rpc/grpc -am
```
