# SSH Implementation Review — Decisions & Fixes

## Date: 2026-08-21

### Decisions Made

#### D1: Wire Format Separation of Concerns
- Packet format ([pktLen][padLen][payload][padding]) is handled by the codec layer
- Cipher layer ONLY encrypts/decrypts data — no MAC or packet format awareness
- Wire format construction is codec responsibility, not cipher responsibility

#### D2: AEAD Cipher Wire Formats (per OpenSSH convention)
- **ChaCha20-Poly1305** (`isPayloadOnly=true`):
  - Wire: `[plaintext_pktLen:4][enc_payload][tag:16]`
  - Cipher receives: `[padLen:1][payload][padding]` (pktLen stripped by codec)
  - Cipher returns: `[enc_payload][tag:16]`
  - Codec builds: `pktLen_bytes + encrypted`

- **AES-GCM** (`isAead=true, isPayloadOnly=false`):
  - Wire: `[plaintext_pktLen:4][encrypted_full_packet][tag:16]`
  - Cipher receives: full packet `[pktLen:4][padLen:1][payload][padding]` with pktLen as AAD
  - Cipher returns: `[encrypted_full_packet][tag:16]`
  - Codec builds: `pktLen_bytes + encrypted`

- **Non-AEAD** (AES-CTR, no-cipher):
  - Wire: `[pktLen:4][padLen:1][payload][padding][mac:var]`
  - Cipher receives: full packet `[pktLen:4][padLen:1][payload][padding]`
  - Cipher returns: encrypted packet (same length)
  - Codec builds: `encrypted + mac`

#### D3: RFC 4253 Compliance
- AEAD: Per RFC 4253 §6.4, encrypted packet replaces plaintext packet on wire
  - OpenSSH uses plaintext pktLen as additional prefix for AEAD (common practice)
  - Tag replaces separate MAC for AEAD ciphers

### Bugs Found & Fixed

#### BUG-001: readPacket wireSize for AEAD ChaCha20 (CRITICAL)
- **Location:** `SshTransport.java:readPacket()` line ~152
- **Problem:** `encryptedLen = packetLength + aeadTagLen` overcounts by 4 bytes
  - ChaCha20: pktLen is NOT encrypted, but calculation includes it
  - Causes server to read 4 extra bytes → blocks waiting for data that never arrives
- **Fix:** `encryptedLen = packetLength - 4 + aeadTagLen` for ChaCha20
  - AES-GCM: `encryptedLen = 4 + packetLength + aeadTagLen` (correct — pktLen IS encrypted)
  - Non-AEAD: `encryptedLen = 4 + packetLength` (correct)

#### BUG-002: MAC Key Derivation Length (MEDIUM)
- **Location:** `SshTransport.java:applyNewKeys()` line ~282
- **Problem:** MAC keys derived with 16 bytes regardless of algorithm
  - HMAC-SHA256 needs 32-byte keys
  - HMAC-SHA2-512 needs 64-byte keys
  - 16-byte keys cause truncated/insecure MAC computation
- **Fix:** Derive MAC keys using algorithm-specific length (32 for sha256, 64 for sha512)

#### BUG-003: constantTimeEquals fixed (already done by other session)
- Was: `a[i] ^ a[i]` (always 0)
- Fixed to: `a[i] ^ b[i]`
