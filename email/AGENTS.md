
# email — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The `email` module is a parent POM (packaging=pom) that groups all email protocol sub-modules under a single build hierarchy.

## Module Structure

```
email/                           <- parent POM (lego-flow-email)
  common/                        <- MIME parsing shared utilities
  smtp/                          <- SMTP sending protocol
  imap/                          <- IMAP4rev2 mailbox access protocol
```

## Parent Chain

```
lego-flow (root)
  -> lego-flow-email (email/pom.xml)
      -> lego-flow-email-common (email/common/pom.xml)
      -> lego-flow-smtp (email/smtp/pom.xml)
      -> lego-flow-imap (email/imap/pom.xml)
```

## Test Counts

| Module | Test Files |
|--------|------------|
| common | 22 |
| smtp | 29 |
| imap | 27 |

## Build Commands

```bash
# Build all email modules
mvn test -pl email/common,email/smtp,email/imap -am

# Build single module
mvn test -pl email/smtp -am
```
