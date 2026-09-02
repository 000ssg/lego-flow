# SSH Implementation Review - Design Decisions & Architecture Notes

## Review Date: 2026-08-21

## 1. Packet Format vs Cipher - Core Design Issue

### Problem
The SshCipher interface contract states: "handles ONLY encrypt/decrypt of packet data - no MAC or packet format awareness. Packet format (pktLen placement, padding) is handled by the codec layer."

ChaCha20-Poly1305 violated this contract by parsing pktLen from the data inside its encrypt() method.

### Decision
Packet format is ALWAYS handled by SshTransportCodec. The cipher receives packet data, not packet format.

### Wire Format by Cipher Type
All ciphers share the same packet structure:
  [pktLen:4][padLen:1][payload:var][padding:var]

AES-CTR (no AEAD):
  Codec builds full packet: [pktLen][padLen][payload][padding]
  Cipher encrypts full packet (same length output)
  Non-AEAD MAC appends: [enc_packet][mac]
  OR ETM MAC appends: [enc_packet][mac] (MAC over encrypted)

AES-GCM (AEAD):
  Codec builds full packet: [pktLen][padLen][payload][padding]
  Codec passes 4-byte pktLen as AAD via setAad()
  Cipher encrypts full packet -> [ciphertext][16-byte tag]
  No separate MAC (AEAD handles integrity)

ChaCha20-Poly1305 (OpenSSH AEAD):
  Codec builds full packet: [pktLen][padLen][payload][padding]
  Cipher's encrypt() receives full packet, outputs [enc_len:4][enc_payload:var][16-byte tag]
  Codec writes wire: [pktLen(unencrypted)][enc_len][enc_payload][tag]

### SshCipher Interface
  public interface SshCipher {
      void init(byte[] key, byte[] iv, boolean encrypt);
      void setSequenceNumber(long seq);      // for AEAD nonce construction
      void setAad(byte[] aad);               // for AEAD (4-byte pktLen for AES-GCM)
      byte[] encrypt(byte[] data);           // FULL packet data (pktLen+pad+payload+pad)
      byte[] decrypt(byte[] data);           // ciphertext+tag (AEAD) or ciphertext
  }

### Separation of Concerns Summary
  Concern                   | Layer
  Packet format (pktLen)    | SshTransportCodec
  Packet padding            | SshTransportCodec
  Encryption/decryption     | SshCipher
  MAC computation           | SshMac
  AEAD tag                  | SshCipher
  ChaCha20 wire format      | SshTransportCodec + ChaCha20Poly1305
  Compression               | SshCompression
  Sequence number tracking  | SshTransportCodec

## 2. SshTransportCodec.encode() - Correct Flow
  1. Compress payload (if compression enabled)
  2. Calculate padding: padLen = blockAligned(4+1+payloadLen) - (4+1+payloadLen)
  3. Build full packet: [pktLen][padLen][payload][padding]
  4. Compute non-AEAD MAC over full packet (if not ETM)
  5. Set sequence number on AEAD cipher
  6. Encrypt full packet -> [ciphertext][tag] (AEAD) or [ciphertext] (CTR)
  7. Compute ETM MAC over encrypted data (if ETM)
  8. Return: [encrypted][tag][mac] (or subset depending on mode)

## 3. SshTransportCodec.decode() - Correct Flow
  1. Strip MAC/Tag overhead from received data
  2. Set sequence number on AEAD cipher
  3. Decrypt -> full packet: [pktLen][padLen][payload][padding]
  4. Verify non-AEAD MAC over full packet (if not ETM)
  5. Verify ETM MAC over encrypted data (if ETM)
  6. Parse packet format: read pktLen, padLen, extract payload, strip padding
  7. Decompress payload (if compression enabled)
  8. Return payload

## 4. DP/DF Service Mode Compatibility
The SshService wrapper provides SSH functionality within the lego-flow service framework:
  Socket mode: SshTransport uses Socket I/O directly
  Service mode: SshService wraps SshTransport, processes ByteBuffer data through service pipeline
  The codec is transport-agnostic - works identically in both modes
  ByteBuffer utility methods (readString, writeString, etc.) are static methods in the codec

## 5. Test Strategy
  Unit tests: Cipher-level encode/decode round-trips (no network)
  Integration tests: Client <-> Server (both using built-in SshServer)
  Interop tests: Client <-> OpenSSH sshd (mature reference implementation)
  Service mode tests: SshService in DP/DF pipeline with ByteBuffer I/O

## 6. ChaCha20-Poly1305 OpenSSH Extension
  Key: 64 bytes (32 ChaCha20 + 32 Poly1305)
  Nonce: 12 bytes (4-byte sequence number + 8 zero bytes)
  pktLen encrypted with ChaCha20 counter=1
  Payload encrypted with ChaCha20 counter=2
  Poly1305 tag: computed over [enc_pktLen || enc_payload]
  Wire: [unenc_pktLen:4][enc_payload_with_tag]

## 2. Cipher Dispatch via isPayloadOnly() Interface Method

### Problem
Codec used `cipher.name().contains("chacha20")` string matching to decide between 
payload-only and full-packet cipher modes. This is fragile (name changes break dispatch).

### Decision
Added `isPayloadOnly()` default method to `SshCipher` interface (returns `false` by default).
`ChaCha20Poly1305` overrides to return `true`. Codec dispatches via `cipher.isPayloadOnly()` 
instead of string matching.

### Files Modified
- `SshCipher.java` — added `isPayloadOnly()` default method
- `ChaCha20Poly1305.java` — overrides `isPayloadOnly()` returning `true`
- `SshTransportCodec.java` — replaced all `name().contains("chacha20")` with `isPayloadOnly()`
- Codec Javadoc updated to document dispatch mechanism

### Principle
Cipher mode is a **capability** (interface method), not a **name** (string match).
New ciphers implementing payload-only mode can be added without codec changes.
