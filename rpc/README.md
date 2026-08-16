
# Lego Flow RPC — Remote Procedure Call Modules

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

Aggregator module for RPC and query protocol implementations in the Lego Flow framework.

## Sub-modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [grpc](grpc/) | `lego-flow-grpc` | gRPC protocol (Google RPC) |
| [graphql](graphql/) | `lego-flow-graphql` | GraphQL query language |

## Test Coverage

| Module | Test Files |
|--------|------------|
| grpc | 31 |
| graphql | 20 |
| **Total** | **51** |

## Build Commands

```bash
# Build all RPC modules
mvn test -pl rpc/grpc,rpc/graphql -am

# Gradle
./gradlew :rpc:grpc:test :rpc:graphql:test
```
