# Lego Flow Media Common -- SDP Parser (RFC 4566)

Shared SDP (Session Description Protocol) parser, serializer, and negotiator used by the RTSP and SIP modules. Pure JDK implementation with no external dependencies.

## Features

- **Full RFC 4566 parsing** -- all line types (v, o, s, i, u, e, p, c, b, t, r, z, k, a, m)
- **Typed model** -- records and enums for Origin, ConnectionInfo, Timing, MediaDescription, RtpMap, etc.
- **Writer** -- serializes back to spec-compliant SDP text with CRLF line endings
- **Offer/answer negotiation** -- RFC 3264 media capability intersection
- **ICE candidates** -- parses `a=candidate:` lines (RFC 5245)
- **DTLS fingerprints** -- parses `a=fingerprint:` (RFC 4572)
- **Payload registry** -- static (RFC 3551) and dynamic payload type tracking
- **Fluent builders** -- `SessionBuilder` and `MediaBuilder` for constructing SDP programmatically

## Package Structure

```
ssg.legoflow.media.common/
  sdp/         Model classes: SessionDescription, Origin, ConnectionInfo, Timing,
               MediaDescription, RtpMap, FormatParameters, Attribute, Direction,
               MediaType, TransportProtocol, IceCandidate, Fingerprint, Bandwidth,
               RepeatTime
  codec/       SdpParser (text to model), SdpWriter (model to text),
               SdpNegotiator (offer/answer)
  payload/     PayloadType definitions, PayloadRegistry
  builder/     SessionBuilder, MediaBuilder (fluent API)
```

## Usage

### Parsing SDP

```java
String sdpText = """
    v=0
    o=alice 2890844526 2890842807 IN IP4 10.0.0.1
    s=Audio Call
    c=IN IP4 10.0.0.1
    t=0 0
    m=audio 49170 RTP/AVP 0 8 96
    a=rtpmap:0 PCMU/8000
    a=rtpmap:8 PCMA/8000
    a=rtpmap:96 opus/48000/2
    a=sendrecv
    """;

SessionDescription session = SdpParser.parse(sdpText);
MediaDescription audio = session.mediaDescriptions().get(0);
RtpMap opus = audio.findRtpMap(96).orElseThrow();
// opus.codec() -> "opus", opus.clockRate() -> 48000
```

### Building SDP

```java
SessionDescription sdp = new SessionBuilder()
    .origin("alice", 2890844526L, 2890842807L, "IN", "IP4", "10.0.0.1")
    .sessionName("Audio Call")
    .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "10.0.0.1"))
    .timing(Timing.PERMANENT)
    .media(new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
        .format(0)
        .format(8)
        .rtpMap(RtpMap.of(96, "opus", 48000, 2))
        .direction(Direction.SENDRECV)
        .build())
    .build();

String text = SdpWriter.write(sdp);
```

### Offer/Answer Negotiation

```java
Optional<SessionDescription> answer = SdpNegotiator.negotiate(offer, answererCapabilities);
```

## Tests

80+ tests covering parser, writer, round-trip, all model types, negotiation, builders, and edge cases.

## Dependencies

- `lego-flow-blocks` (core framework)
- JUnit 5, AssertJ (test only)

## Documentation

- [Requirements](doc/REQUIREMENTS.md)
