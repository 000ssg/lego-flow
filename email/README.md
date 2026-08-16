
# Lego Flow Email — Email Protocol Modules

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

Aggregator module for email protocol implementations in the Lego Flow framework.

## Sub-modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [common](common/) | `lego-flow-email-common` | MIME parsing (RFC 2045-2049) |
| [smtp](smtp/) | `lego-flow-smtp` | SMTP protocol (RFC 5321) |
| [imap](imap/) | `lego-flow-imap` | IMAP4rev2 (RFC 9051) |

## Test Coverage

| Module | Test Files |
|--------|------------|
| common | 22 |
| smtp | 29 |
| imap | 27 |
| **Total** | **78** |

## Build Commands

```bash
# Build all email modules
mvn test -pl email/common,email/smtp,email/imap -am

# Gradle
./gradlew :email:common:test :email:smtp:test :email:imap:test
```
