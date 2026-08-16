
# media — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The `media` module is a parent POM (packaging=pom) that groups all media and streaming protocol sub-modules under a single build hierarchy.

## Module Structure

```
media/                           <- parent POM (lego-flow-media)
  common/                        <- SDP parser shared utilities
  rtsp/                          <- RTSP 2.0 streaming protocol
  rtp/                           <- RTP real-time transport
  sip/                           <- SIP session initiation
```

## Parent Chain

```
lego-flow (root)
  -> lego-flow-media (media/pom.xml)
      -> lego-flow-media-common (media/common/pom.xml)
      -> lego-flow-rtsp (media/rtsp/pom.xml)
      -> lego-flow-rtp (media/rtp/pom.xml)
      -> lego-flow-sip (media/sip/pom.xml)
```

## Test Counts

| Module | Test Files |
|--------|------------|
| common | 20 |
| rtsp | 20 |
| rtp | 9 |
| sip | 17 |

## Build Commands

```bash
# Build all media modules
mvn test -pl media/common,media/rtsp,media/rtp,media/sip -am

# Build single module
mvn test -pl media/rtsp -am
```
