# Media Common Module -- Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 180
- **Dependencies**: blocks (core framework)
- **Standards**: RFC 4566 (SDP), RFC 3264 (Offer/Answer), RFC 3551 (RTP Payload Types), RFC 5245 (ICE), RFC 4572 (DTLS Fingerprint)

---

## Requirements

### SDP Model (sdp package)
1. Represent complete SDP session description with all session-level fields: version, origin, session name, session info, URI, email, phone, connection info, bandwidth, timing, repeat time, timezone adjustments, encryption key, attributes, media descriptions
2. Represent media descriptions with media type, port, port count, transport protocol, format list, title, connection info, bandwidth, direction, RTP maps, format parameters, ICE candidates, fingerprint, and generic attributes
3. Model SDP origin (o= line) with username, session ID, version, network type, address type, and address
4. Model connection info (c= line) with support for unicast, multicast with TTL, and multicast with TTL and address count
5. Model timing (t= line) with NTP timestamps; provide PERMANENT constant for unbounded sessions
6. Model bandwidth (b= line) with modifier (CT, AS, extension) and value in kbps
7. Model repeat time (r= line) with compact notation support
8. Model generic attributes (a= line) in both property-style (no value) and value-style (name:value) forms
9. Model RTP map (a=rtpmap:) with payload type, codec name, clock rate, and optional channel count; validate payload type 0-127 and positive clock rate
10. Model format parameters (a=fmtp:) with semicolon-separated key=value parsing and raw value preservation
11. Model ICE candidates (a=candidate:) per RFC 5245 with foundation, component ID, transport, priority, address, port, type, and optional related address/port
12. Model DTLS fingerprint (a=fingerprint:) per RFC 4572 with hash function and hash value
13. Enumerate media directions: sendrecv, sendonly, recvonly, inactive
14. Enumerate media types: audio, video, text, application, message
15. Enumerate transport protocols: RTP/AVP, RTP/SAVP, RTP/AVPF, RTP/SAVPF, UDP, TCP, TCP/RTP/AVP
16. All model classes must be immutable; collections must use List.copyOf() or Collections.unmodifiableMap()

### SDP Parser (codec.SdpParser)
1. Parse all SDP line types: v=, o=, s=, i=, u=, e=, p=, c=, b=, t=, r=, z=, k=, a=, m=
2. Distinguish session-level and media-level lines based on m= line boundaries
3. Parse media attributes into typed objects: rtpmap, fmtp, candidate, fingerprint, direction
4. Preserve all attributes as generic Attribute instances for lossless round-trip
5. Handle both CRLF and LF line endings
6. Strip leading whitespace from lines (tolerant parsing)
7. Silently ignore unknown line types (forward compatibility)
8. Validate required fields: o= (origin) and s= (session name) must be present
9. Parse multicast connection addresses with TTL and address count (slash notation)
10. Support multiple media descriptions per session

### SDP Writer (codec.SdpWriter)
1. Serialize SessionDescription to SDP text with CRLF line endings
2. Output lines in RFC 4566 prescribed order: v, o, s, i, u, e, p, c, b, t, r, z, k, a, m
3. Write media-level attributes from the generic attributes list to preserve original order
4. Omit optional fields that are absent (sessionInfo, uri, email, phone, etc.)

### SDP Negotiator (codec.SdpNegotiator)
1. Implement RFC 3264 offer/answer negotiation
2. Match offered media descriptions to answerer capabilities by media type and transport protocol
3. Intersect formats using codec name (case-insensitive) and clock rate as matching key
4. Reverse direction in answer: sendonly becomes recvonly, recvonly becomes sendonly, sendrecv stays sendrecv, inactive stays inactive
5. Reject media descriptions with no compatible formats by setting port to 0
6. Include fmtp parameters for intersected dynamic formats
7. Return Optional.empty() if no media is compatible at all

### Fluent Builders (builder package)
1. SessionBuilder: fluent API for constructing SessionDescription with sensible defaults (version=0, session name=" ", auto-add Timing.PERMANENT if no timing specified)
2. SessionBuilder: support all session-level fields via method chaining
3. SessionBuilder: validate that origin is set before build
4. MediaBuilder: fluent API for constructing MediaDescription with defaults (portCount=1, direction=SENDRECV)
5. MediaBuilder: rtpMap() automatically adds format number and creates corresponding attribute
6. MediaBuilder: formatParameters(), iceCandidate(), fingerprint() automatically create corresponding attributes
7. MediaBuilder: direction attribute automatically added during build()

### Payload Registry (payload package)
1. Define PayloadType record with number (0-127), codec, clock rate, optional channels, media type
2. Pre-define all 18 static payload types from RFC 3551 (PCMU, GSM, G723, DVI4x2, LPC, PCMA, G722, L16x2, QCELP, CN, G729, JPEG, H261, MPV, MP2T, H263)
3. Support dynamic payload type registration (96-127) at runtime
4. Lookup by number: check dynamic types first, then static
5. Validate payload type range (0-127) and dynamic range (96-127) on registration
6. Provide clearDynamic() to reset runtime registrations

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
