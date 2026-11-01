---
name: protocol-wire-format-debugging
description: Debug protocol interop with wire capture byte comparison.
version: 1.0.0
author: Hermes Agent
license: MIT
metadata:
  hermes:
    tags: [protocol, wire-format, binary-protocol, amqp, debugging, compliance]
    related_skills: [systematic-debugging, test-driven-development]
---

# Protocol Wire Format Debugging

## Trigger

Implementing or debugging a client for a binary protocol (AMQP, MQTT, DNS, HTTP/2, etc.) and it fails to interop with a real server. Symptoms: server closes connection, returns error frames, or behaves differently than the reference client.

## Principle

**Don't guess at wire format from the spec alone.** Specs are ambiguous about reserved fields, bit packing order, endianness, and string length encoding. A known-good reference implementation is the ground truth. Capture its exact bytes, compare byte-by-byte with yours, and fix every mismatch.

## Two-Client Wire Capture Pattern

### 1. Set up a TCP pass-through proxy

Create a TCP proxy that sits between your client and the real server, logging every byte in both directions:

```java
class ByteCapturingProxy {
    List<byte[]> clientToServer = new ArrayList<>();
    List<byte[]> serverToClient = new ArrayList<>();

    void start(int localPort, String remoteHost, int remotePort) {
        var server = new ServerSocket(localPort);
        Thread.ofVirtual().start(() -> {
            Socket client = server.accept();
            Socket remote = new Socket(remoteHost, remotePort);
            Thread.ofVirtual().start(() -> pump(client.getInputStream(), remote.getOutputStream(), clientToServer));
            Thread.ofVirtual().start(() -> pump(remote.getInputStream(), client.getOutputStream(), serverToClient));
        });
    }
}
```

### 2. Run reference client through proxy

Connect the known-good client through the proxy. Capture all client→server bytes.

### 3. Run your client through proxy

Connect your implementation through a second proxy. Capture all client→server bytes.

### 4. Compare byte-by-byte

For each corresponding frame, compare the hex dumps. Any byte that differs is a compliance bug.

### 5. Validate each capture before trusting it

A capture is only valid if it contains application-level frames. **Decode it before using it**:
a few hundred bytes of repeated protocol headers (with SASL/auth attempts and no session frames)
is a fingerprint of an auth/reconnect loop — wrong credentials is the usual cause — not a session.
If the capture harness runs an external CLI and swallows its failure (timeout → write whatever
bytes were grabbed), the test goes green on garbage. Verify the external process's exit/status
independently of the test result, and confirm the capture contains the session frames the test
claims to have captured.

## Common Wire Format Pitfalls

| Pitfall | Spec says | Easy mistake | Example |
|---------|-----------|--------------|---------|
| **Reserved fields** | `reserved-1` short = 0 | Omitted entirely | AMQP 0-9-1: every method starts with 2 zero bytes |
| **Bit packing** | Bits packed left-to-right: `(bit ? 0x80 : 0) >> index` | Each bit as separate byte | AMQP flags: 6 booleans → 1 byte, not 6 |
| **String length** | `shortstr`=1 byte len, `longstr`=4 byte len | Wrong size prefix | AMQP vhost uses longstr |
| **Integer size** | Field is `long` (8 bytes) | Sending `int` (4 bytes) | AMQP `basic.qos` prefetch-size is long |
| **Endianness** | All multi-byte fields big-endian | Native order | ByteBuffer must be BIG_ENDIAN |
| **Table keys** | Keys are `shortstr`, values have type prefix | Keys as `longstr` | AMQP table: 1-byte key len |

## Differential Testing

Isolate a single operation to eliminate handshake noise:
1. Reference client → perform operation with `ref-{ts}` naming
2. Your client → same operation with `own-{ts}` naming
3. Compare only the target method frame bytes

## Server Response Signals

- `channel.close` → wire format error in your request
- `precondition-failed` → wire OK, request parameters invalid
- Compare server→client bytes to confirm understanding

## Checklist

- [ ] Each capture validated as a real session (application-level frames present — not an auth/reconnect loop of repeated protocol headers)
- [ ] Reference client capture obtained
- [ ] Your client capture obtained (same operation, same params)
- [ ] Reserved fields present and zeroed
- [ ] Bits packed left-to-right into single byte(s)
- [ ] String length prefixes match spec
- [ ] Integer sizes match spec
- [ ] Endianness is big-endian
- [ ] Frame end octet present
- [ ] Method class-id and method-id in correct position

## References

See `references/amqp-091-wire-format.md` for AMQP 0-9-1 specific details.
