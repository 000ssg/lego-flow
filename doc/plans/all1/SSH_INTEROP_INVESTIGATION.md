# SSH Interop Investigation Report

## Summary

SSH interop testing revealed two distinct scenarios:
1. **Lego Flow ↔ Lego Flow**: ✅ WORKS (24 integration tests pass)
2. **Lego Flow ↔ MINA SSHD**: ❌ FAILS (key exchange failure)
3. **Lego Flow ↔ OpenSSH**: ⚠️ PARTIAL (version exchange works, key exchange fails)

## Root Cause Analysis

### MINA Interop Failure
- **Error**: `Unexpected end of stream` at `SshTransport.java:291` during `readPacket()`
- **Timing**: After client sends KEXINIT, MINA sends DEBUG messages but doesn't respond with KEXINIT
- **Impact**: All MINA interop tests fail (6 tests)

### OpenSSH Interop Failure  
- **Error**: `AES-256-GCM decryption failed: Tag mismatch` during key exchange
- **Timing**: After client sends KEXDH_INIT, OpenSSH responds but decryption fails
- **Impact**: Key exchange fails, client disconnects

## Fixes Applied

### 1. Empty Signature in KEXDH_REPLY
**File**: `network/ssh/src/main/java/ssg/legoflow/ssh/transport/SshTransport.java`
**Problem**: Server sent empty signature in KEXDH_REPLY (line 402-404)
**Fix**: Added proper signature using `exchangeHash` instead of `sessionId`
```java
byte[] signature = serverHostKey.sign(exchangeHash);
```

### 2. Added HostKeyAlgorithm Factory Method
**File**: `network/ssh/src/main/java/ssg/legoflow/ssh/hostkey/HostKeyAlgorithm.java`
**Fix**: Added `create(Path keyPath)` static factory method

### 3. Removed Debug Statements
**File**: `network/ssh/src/main/java/ssg/legoflow/ssh/transport/SshTransport.java`
**Fix**: Removed `LOG.debug("FLUSH! [readPacket ENTER]")` and other debug prints

## Remaining Issues

### Algorithm Negotiation Mismatch
The key exchange failure suggests algorithm negotiation is working, but the derived keys don't match. Possible causes:
1. **Sequence number handling**: AEAD ciphers require exact sequence number tracking
2. **Key derivation**: SSH key derivation (K1, K2, IVs) may have implementation bugs
3. **Packet format**: Subtle differences in packet encoding/decoding

### MINA-Specific Issues
MINA appears to have its own behavior:
- Sends DEBUG messages before KEXINIT
- May expect specific algorithm preferences
- May have issues with our KEXINIT format

## Test Results

### Passing Tests (718 total)
- SshIntegrationTest: 24 tests ✅
- SshServerTest: 18 tests ✅
- SshClientTest: 13 tests ✅
- All unit tests: ✅

### Failing Tests (6 total)
- SshServerInteropTest: 6 MINA interop tests ❌
  - testVersionExchangeWithMinaServer
  - testAuthWithMinaServer
  - testSessionWithMinaServer
  - testDataTransferWithMinaServer
  - testCipherAes128CtrWithMinaServer
  - testDisconnectWithMinaServer

### Skipped Tests (Interop module)
- OpenSSH interop tests skipped (require external sshd container)

## Recommendations

### Short-term
1. Re-disable MINA interop tests with documented root cause
2. Enable OpenSSH interop tests when container is available
3. Document the interoperability gap

### Long-term
1. **Packet-level debugging**: Capture wire bytes during MINA/OpenSSH connections
2. **Compare with reference**: Use Wireshark or similar to capture protocol exchange
3. **Implement missing features**: Check if we support all required algorithms
4. **Test with more servers**: Test against Dropbear, libssh-server, etc.

### Key Exchange Debugging
To fix the key exchange issue:
1. Enable detailed logging in SshTransportCodec
2. Compare packet bytes with reference implementation
3. Verify key derivation matches RFC 4253 Section 8
4. Check sequence number handling for AEAD ciphers

## Files Modified

1. `network/ssh/src/main/java/ssg/legoflow/ssh/transport/SshTransport.java`
   - Fixed signature in buildKexReplyMessage()
   - Removed debug statements
   - Fixed variable declarations

2. `network/ssh/src/main/java/ssg/legoflow/ssh/hostkey/HostKeyAlgorithm.java`
   - Added create() factory method

3. `network/ssh/src/test/java/ssg/legoflow/ssh/SshServerInteropTest.java`
   - Enabled MINA interop tests (now failing with documented cause)

## Next Steps

1. Create packet-level debug test to capture wire bytes
2. Compare with OpenSSL/MINA reference implementations
3. Fix key derivation if needed
4. Re-enable interop tests once fixed
5. Add comprehensive interop test coverage (session, terminal, SFTP, port forwarding)
