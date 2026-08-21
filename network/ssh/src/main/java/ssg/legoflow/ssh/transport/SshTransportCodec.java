package ssg.legoflow.ssh.transport;

import ssg.legoflow.ssh.cipher.SshCipher;
import ssg.legoflow.ssh.mac.SshMac;
import ssg.legoflow.ssh.compression.SshCompression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * SSH binary packet encoding and decoding per RFC 4253 section 6.
 *
 * <p>Handles packet format (pktLen, padLen, padding) and delegates
 * encryption/decryption to cipher implementations. The codec constructs
 * the full packet structure before passing it to ciphers.
 *
 * <p>Cipher modes (dispatched via {@code SshCipher.isPayloadOnly()}):
 * <ul>
 *   <li>AES-CTR: cipher receives full packet, returns encrypted packet (same length)</li>
 *   <li>AES-GCM: cipher receives full packet with pktLen as AAD, returns [ciphertext][16-byte tag]</li>
 *       Codec prepends plaintext pktLen to wire format: [pktLen][ct][tag]</li>
 *   <li>ChaCha20-Poly1305: cipher receives payload only, returns [encPayload][16-byte tag]</li>
 *       Codec prepends plaintext pktLen to wire format: [pktLen][encPayload][tag]</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class SshTransportCodec {

    private static final Logger LOG = LoggerFactory.getLogger(SshTransportCodec.class);

    public static final int MAX_PACKET_SIZE = 35000;
    public static final int MIN_PADDING = 4;
    public static final int MAX_PADDING = 255;

    private final SecureRandom random;
    private SshCipher encodeCipher;
    private SshCipher decodeCipher;
    private SshMac encodeMac;
    private SshMac decodeMac;
    private SshCompression compression;
    private long inputSequenceNumber;
    private long blocksProcessed;
    private long outputSequenceNumber;
    private byte[] sessionKeyMaterial;
    private byte[] exchangeHash;
    private byte[] decodeKey;
    private byte[] decodeIV;
    private int wirePktLen = 0;  // pktLen from wire prefix (for no-cipher path)

    public SshTransportCodec() {
        this.random = new SecureRandom();
    }

    public void setCipher(SshCipher cipher) {
        this.encodeCipher = cipher;
        this.decodeCipher = cipher;
    }

    public void setEncodeCipher(SshCipher cipher) {
        this.encodeCipher = cipher;
    }

    public void setDecodeCipher(SshCipher cipher) {
        this.decodeCipher = cipher;
    }

    public void setEncodeMac(SshMac mac) { this.encodeMac = mac; }
    public void setDecodeMac(SshMac mac) { this.decodeMac = mac; }
    public void setCompression(SshCompression compression) { this.compression = compression; }

    public long outputSequenceNumber() { return outputSequenceNumber; }
    public long inputSequenceNumber() { return inputSequenceNumber; }
    public long blocksProcessed() { return blocksProcessed; }
    public byte[] getSessionKeyMaterial() { return sessionKeyMaterial; }
    public byte[] getExchangeHash() { return exchangeHash; }
    public SshCipher getEncodeCipher() { return encodeCipher; }
    public SshCipher getDecodeCipher() { return decodeCipher; }
    public SshMac getDecodeMac() { return decodeMac; }

    /** Derives key material per RFC 4253 §7.2, defaulting to 20 bytes. */
    public static byte[] deriveKey(byte[] km, byte[] h, char c) {
        return deriveKey(km, h, c, 20);
    }

    /** Derives key material per RFC 4253 §7.2. */
    public static byte[] deriveKey(byte[] km, byte[] h, char c, int length) {
        int offset = 0;
        int count = 1;
        byte[] key = new byte[length];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            while (offset < length) {
                md.reset();
                md.update(km);
                md.update(h);
                md.update((byte) c);
                md.update(km.length >= 20 ? Arrays.copyOfRange(km, 0, 20) : pad(km, 20));
                md.update(ByteBuffer.allocate(4).putInt(count++).array());
                byte[] hash = md.digest();
                int copy = Math.min(hash.length, length - offset);
                System.arraycopy(hash, 0, key, offset, copy);
                offset += copy;
            }
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
        return key;
    }

    private static byte[] pad(byte[] src, int len) {
        byte[] result = new byte[len];
        System.arraycopy(src, 0, result, 0, Math.min(src.length, len));
        return result;
    }

    public void setSessionKeyMaterial(byte[] km, byte[] h) {
        this.sessionKeyMaterial = km;
        this.exchangeHash = h;
    }

    public void setDecodeKeyAndIV(byte[] key, byte[] iv) {
        this.decodeKey = key;
        this.decodeIV = iv;
    }

    public String getDecodeCipherName() {
        return decodeCipher != null ? decodeCipher.name() : null;
    }

    public byte[] getDecodeKey() { return decodeKey; }
    public byte[] getDecodeIV() { return decodeIV; }

    /** Returns block size for cipher (16 default for AEAD, 8 for ChaCha20). */
    public int blockSize() {
        if (encodeCipher != null) return encodeCipher.blockSize();
        return 16;
    }

    /** Returns MAC length for non-AEAD modes. */
    public int macLength() {
        if (decodeMac != null && !decodeMac.isEncryptThenMac()) {
            return decodeMac.macLength();
        }
        return 0;
    }

    /** Returns AEAD tag length. */
    public int aeadTagLength() {
        if (decodeCipher != null && decodeCipher.isAead()) {
            return decodeCipher.authTagLength();
        }
        return 0;
    }

    /**
     * Encodes a payload into SSH wire format.
     *
     * <p>Codec responsibilities:
     * <ul>
     *   <li>Compression</li>
     *   <li>Packet formatting ([pktLen][padLen][payload][padding])</li>
     *   <li>Padding alignment to block boundary</li>
     *   <li>Cipher dispatch (full-packet or payload-only mode)</li>
     *   <li>MAC computation (non-AEAD or ETM)</li>
     *   <li>AEAD wire format: plaintext pktLen prefix for all AEAD ciphers</li>
     * </ul>
     */
    public byte[] encode(byte[] payload) {
        Objects.requireNonNull(payload);

        // 1. Compress
        byte[] cp = payload;
        if (compression != null) cp = compression.compress(payload);

        // 2. Calculate padding
        int bs = encodeCipher != null ? encodeCipher.blockSize() : 8;
        if (bs < 8) bs = 8;

        int plen = 4 + 1 + cp.length;
        int padLen = bs - (plen % bs);
        if (padLen < MIN_PADDING) padLen += bs;

        int packetLen = 1 + padLen + cp.length;
        if (packetLen > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Packet too large: " + packetLen);
        }

        // 3. Build full packet: [pktLen][padLen][payload][padding]
        ByteBuffer buf = ByteBuffer.allocate(4 + packetLen);
        buf.putInt(packetLen);
        buf.put((byte) padLen);
        buf.put(cp);
        byte[] padding = new byte[padLen];
        random.nextBytes(padding);
        buf.put(padding);

        byte[] packetBytes = buf.array();
        byte[] encPktLenBytes = ByteBuffer.allocate(4).putInt(packetLen).array();

        LOG.debug("[CODEC-ENC] seq=" + outputSequenceNumber + " cipher="
            + (encodeCipher != null ? encodeCipher.name() : "none")
            + " mac=" + (encodeMac != null ? encodeMac.name() : "none")
            + " AEAD=" + (encodeCipher != null && encodeCipher.isAead())
            + " payloadOnly=" + (encodeCipher != null && encodeCipher.isPayloadOnly())
            + " ETM=" + (encodeMac != null && encodeMac.isEncryptThenMac())
            + " pktBytes=" + packetBytes.length
            + " pktLen=" + packetLen);

        // 4. Compute non-AEAD MAC (before encryption, over plaintext packet)
        byte[] macBytes = new byte[0];
        if (encodeMac != null && !encodeMac.isEncryptThenMac()) {
            macBytes = encodeMac.compute(outputSequenceNumber, packetBytes);
            LOG.debug("[CODEC-MAC] non-ETM seq=" + outputSequenceNumber);
        }

        // 5. Encrypt — cipher dispatch
        byte[] encrypted;
        if (encodeCipher != null) {
            if (encodeCipher.isAead()) {
                encodeCipher.setSequenceNumber(outputSequenceNumber);
            }

            if (encodeCipher.isAead() && encodeCipher.isPayloadOnly()) {
                // ChaCha20-Poly1305: payload-only mode
                LOG.debug("[CODEC-ENC-PKT] fullPacketHex=" + bytesToHex(packetBytes, Math.min(64, packetBytes.length)) + " pktLenField=" + packetLen);
                // Packet format: [pktLen:4][padLen:1][payload][padding]
                // Cipher receives: [padLen:1][payload][padding] (pktLen sent in clear)
                byte[] payloadOnly = Arrays.copyOfRange(packetBytes, 4, packetBytes.length);
                LOG.debug("[CODEC-ENC] ChaCha20 payloadOnly=" + payloadOnly.length + " pktLen=" + packetLen);
                encrypted = encodeCipher.encryptPayload(payloadOnly);
                LOG.debug("[CODEC-ENC] ChaCha20 encrypted payload+tag=" + encrypted.length);
            } else if (encodeCipher.isAead()) {
                // AES-GCM: payload mode (per OpenSSH convention)
                // Packet format: [pktLen:4][padLen:1][payload][padding]
                // Cipher receives: [padLen:1][payload][padding] (skip pktLen prefix)
                // AAD = plaintext pktLen
                LOG.debug("[CODEC-ENC] AES-GCM encrypt pktLen=" + packetLen + " payloadLen=" + (packetBytes.length - 4) + " aad=" + bytesToHex(encPktLenBytes, 4));
                encodeCipher.setAad(encPktLenBytes);
                byte[] payloadOnly = Arrays.copyOfRange(packetBytes, 4, packetBytes.length);
                LOG.debug("[CODEC-ENC] AES-GCM payloadOnlyLen=" + payloadOnly.length + " payloadOnly=" + bytesToHex(payloadOnly, Math.min(64, payloadOnly.length)));
                encrypted = encodeCipher.encryptWithAad(payloadOnly, encPktLenBytes);
                LOG.debug("[CODEC-ENC] AES-GCM encrypted=" + encrypted.length
                    + " (payload=" + payloadOnly.length + ", tag=" + (encrypted.length - payloadOnly.length) + ")");
            } else {
                // AES-CTR: full packet mode, no AAD
                encrypted = encodeCipher.encrypt(packetBytes);
                LOG.debug("[CODEC-ENC] AES-CTR encrypted=" + encrypted.length);
            }
        } else {
            // No cipher — send plaintext packet as-is
            encrypted = Arrays.copyOfRange(packetBytes, 4, packetBytes.length);
        }

        // 6. Compute ETM MAC (over encrypted data)
        if (encodeMac != null && encodeMac.isEncryptThenMac()) {
            LOG.debug("[CODEC-MAC] ETM-ENC seq=" + outputSequenceNumber);
            macBytes = encodeMac.compute(outputSequenceNumber, encrypted);
            LOG.debug("[CODEC-MAC] ETM computed=" + bytesToHex(macBytes, 16));
        }

        LOG.debug("[CODEC-SEQ] outputSequenceNumber: " + outputSequenceNumber + " -> " + (outputSequenceNumber + 1));
        outputSequenceNumber++;

        // 7. Build wire format
        // ChaCha20 (AEAD payload-only): wire = [unenc_pktLen:4][encrypted_payload][tag]
        //   encrypted.length = pktLen + tagLen, wire = 4 + encrypted.length + macLen
        // AES-GCM (AEAD full-packet): wire = [unenc_pktLen:4][encrypted_full_packet][tag]
        //   encrypted.length = packetBytes.length + tagLen, wire = 4 + encrypted.length + macLen
        // Non-AEAD (AES-CTR, no-cipher): wire = [pktLen:4][padLen:1][payload][padding][mac]
        //   encrypted = packetBytes (includes pktLen), wire = encrypted.length + macLen
        int macLen = macBytes.length;
        boolean hasWirePrefix = (encodeCipher != null && encodeCipher.isAead());
        byte[] result;
        if (hasWirePrefix) {
            // AEAD: prepend plaintext pktLen
            result = new byte[4 + encrypted.length + macLen];
            System.arraycopy(encPktLenBytes, 0, result, 0, 4);
            System.arraycopy(encrypted, 0, result, 4, encrypted.length);
            if (macLen > 0) {
                System.arraycopy(macBytes, 0, result, 4 + encrypted.length, macLen);
            }
        } else {
            // Non-AEAD: [plaintext_pktLen:4][encPacket:packetLen][mac]
            // The packetLen already includes the pktLen field (pktLen = packetLen from the spec)
            result = new byte[4 + encrypted.length + macLen];
            System.arraycopy(encPktLenBytes, 0, result, 0, 4);
            System.arraycopy(encrypted, 0, result, 4, encrypted.length);
            if (macLen > 0) {
                System.arraycopy(macBytes, 0, result, 4 + encrypted.length, macLen);
            }
        }
        LOG.debug("[CODEC-ENC] wire total=" + result.length + " (prefix=" + (hasWirePrefix ? "yes" : "no") + ")");
        return result;
    }

    /**
     * Decodes SSH wire format back to payload.
     *
     * <p>Codec responsibilities:
     * <ul>
     *   <li>MAC/Tag verification</li>
     *   <li>Cipher dispatch (full-packet or payload-only mode)</li>
     *   <li>Packet format parsing ([pktLen][padLen][payload][padding])</li>
     *   <li>Padding removal</li>
     *   <li>Decompression</li>
     * </ul>
     */
    public byte[] decode(byte[] data) {
        LOG.debug("[CODEC-DEC] data.length=" + data.length
            + " cipher=" + (decodeCipher != null ? decodeCipher.name() : "none")
            + " mac=" + (decodeMac != null ? decodeMac.name() : "none")
            + " AEAD=" + (decodeCipher != null && decodeCipher.isAead())
            + " payloadOnly=" + (decodeCipher != null && decodeCipher.isPayloadOnly())
            + " seq=" + inputSequenceNumber
            + " decodeCipherPresent=" + (decodeCipher != null));
        LOG.debug("[CODEC DECODE] data.length={} AEAD tag={} mac={}",
            data.length, aeadTagLength(), decodeMac != null ? decodeMac.macLength() : 0);

        Objects.requireNonNull(data);

        // Determine overhead (MAC tag or AEAD tag) and split
        int aeadTagLen = (decodeCipher != null && decodeCipher.isAead()) ? decodeCipher.authTagLength() : 0;
        int macLen = (decodeCipher != null && !decodeCipher.isAead() && decodeMac != null && !decodeMac.isEncryptThenMac()) ? decodeMac.macLength() : 0;

        // Wire format:
        // ChaCha20 (AEAD): [unenc_pktLen:4][encrypted_payload][tag]
        // AES-GCM (AEAD): [unenc_pktLen:4][encrypted_full_packet][tag]
        // Non-AEAD (AES-CTR, no-cipher): [pktLen:4][encrypted_data][mac]
        
        boolean hasWirePrefix = (decodeCipher != null && decodeCipher.isAead());
        int pktLen;
        byte[] encrypted;
        byte[] authData; // MAC only (tag is inside encrypted for AEAD)
        int tagLen = (decodeCipher != null && decodeCipher.isAead()) ? decodeCipher.authTagLength() : 0;
        if (hasWirePrefix) {
            // AEAD wire format: [unenc_pktLen:4][encrypted_payload_or_full_packet][tag]
            // The plaintext pktLen is sent in clear at the wire prefix.
            // Codec receives full wire with prefix — strip prefix, compute pktLen from encrypted data.
            int wirePktLen = ByteBuffer.wrap(data, 0, 4).getInt();
            this.wirePktLen = wirePktLen;  // stored in field for no-cipher path
            // Strip the plaintext pktLen prefix: encrypted = data[4:]
            encrypted = Arrays.copyOfRange(data, 4, data.length);
            // pktLen is the plaintext packet length (padLen + payload + padding).
            // AEAD cipher outputs (pktLen) ciphertext + tagLen.
            // So encrypted.length = pktLen + tagLen, meaning pktLen = encrypted.length - tagLen.
            pktLen = encrypted.length - tagLen;
            if (pktLen != wirePktLen) {
                LOG.warn("[CODEC-DEC-AEAD-LEN-MISMATCH] wirePktLen=" + wirePktLen + " computedPktLen=" + pktLen + " encryptedLen=" + encrypted.length + " tagLen=" + tagLen);
            }
            authData = new byte[0];
            LOG.debug("[CODEC-DEC] AEAD wirePktLen=" + wirePktLen + " computedPktLen=" + pktLen
                + " encryptedLen=" + encrypted.length + " tagLen=" + tagLen);
        } else {
            // Non-AEAD: data may include plaintext prefix or not
            // Wire: [prefix:4][packet:packetLen][mac] or [packet:packetLen][mac]
            // Detect: if data.length == pktLen + macLen → no prefix; if data.length == 4 + pktLen + macLen → has prefix
            pktLen = ByteBuffer.wrap(data, 0, 4).getInt();
            wirePktLen = pktLen;
            LOG.debug("[CODEC-DEC] Non-AEAD pktLen=" + pktLen + " dataLen=" + data.length);
            int packetStart = (data.length == pktLen + macLen) ? 0 : 4;
            LOG.debug("[CODEC-DEC] Non-AEAD packetStart=" + packetStart);
            int packetEnd = data.length - macLen;
            encrypted = Arrays.copyOfRange(data, packetStart, packetEnd);
            authData = macLen > 0 ? Arrays.copyOfRange(data, packetEnd, data.length) : new byte[0];
            LOG.debug("[CODEC-DEC] Non-AEAD encrypted=" + encrypted.length + " (from " + packetStart + " to " + packetEnd + ")");
        }

        LOG.debug("[CODEC-DEC PARSE] pktLen=" + pktLen + " encryptedLen=" + encrypted.length
            + " aead=" + aeadTagLen + " mac=" + macLen);

        // 2. Decrypt — cipher dispatch
        byte[] packetBytes;
        if (decodeCipher != null) {
            LOG.debug("[CODEC-DEC] Before setSequenceNumber: seq=" + inputSequenceNumber);
            decodeCipher.setSequenceNumber(inputSequenceNumber);
            LOG.debug("[CODEC-DEC] After setSequenceNumber: seq=" + inputSequenceNumber);

            if (decodeCipher.isAead() && decodeCipher.isPayloadOnly()) {
                // ChaCha20-Poly1305: payload-only mode
                // Wire: [unenc_pktLen:4][encPayloadWithTag]
                // Cipher receives only the encrypted payload (pktLen NOT encrypted)
                byte[] encPayloadWithTag = encrypted;
                LOG.debug("[CODEC-DEC] ChaCha20 encPayloadWithTag=" + encPayloadWithTag.length);
                packetBytes = decodeCipher.decryptPayload(encPayloadWithTag);
                LOG.debug("[CODEC-DEC] ChaCha20 decrypted (payload-only)=" + packetBytes.length);
                // packetBytes = [padLen:1][payload][padding] — NO pktLen prefix
            } else if (decodeCipher.isAead()) {
                // AES-GCM: payload mode (per OpenSSH convention)
                // Wire: [unenc_pktLen:4][encrypted_payload_with_tag]
                // Decrypted = [padLen:1][payload][padding] (no pktLen prefix)
                // pktLen was already computed in the AEAD branch — use it for AAD.
                LOG.debug("[CODEC-DEC-PKT] dataHex=" + bytesToHex(data, Math.min(64, data.length)) + " encryptedHex=" + bytesToHex(encrypted, Math.min(64, encrypted.length)));
                byte[] pktLenBytes = ByteBuffer.allocate(4).putInt(pktLen).array();
                LOG.debug("[CODEC-DEC] AES-GCM using pktLenFromAEAD=" + pktLen 
                    + " encryptedLen=" + encrypted.length + " tagLen=" + tagLen
                    + " seq=" + inputSequenceNumber);
                decodeCipher.setAad(pktLenBytes);
                LOG.debug("[CODEC-DEC] AES-GCM encryptedOnly=" + bytesToHex(encrypted, Math.min(64, encrypted.length)) + " encryptedLen=" + encrypted.length);
                byte[] decrypted = decodeCipher.decryptWithAad(encrypted, pktLenBytes);
                LOG.debug("[CODEC-DEC CIPHER] AES-GCM seq=" + inputSequenceNumber
                    + " decLen=" + decrypted.length);
                // Reconstruct: [pktLen:4][padLen:1][payload][padding]
                packetBytes = new byte[4 + decrypted.length];
                System.arraycopy(pktLenBytes, 0, packetBytes, 0, 4);
                System.arraycopy(decrypted, 0, packetBytes, 4, decrypted.length);
                LOG.debug("[CODEC-DEC RECONSTRUCT] pktLen=" + pktLen + " total=" + packetBytes.length);
            } else {
                // AES-CTR: standard full-packet mode
                packetBytes = decodeCipher.decrypt(encrypted);
                LOG.debug("[CODEC-DEC CIPHER] AES-CTR seq=" + inputSequenceNumber
                    + " plainLen=" + packetBytes.length);
                // packetBytes = [pktLen:4][padLen:1][payload][padding]
            }
        } else {
            // No cipher — encrypted is plaintext packet bytes
            packetBytes = encrypted;
            LOG.debug("[CODEC-DEC NO-CIPHER] pktLen=" + pktLen + " packetBytesLen=" + packetBytes.length
                + " first4=" + bytesToHex(Arrays.copyOf(packetBytes, Math.min(16, packetBytes.length)), 16));
        }

        // 3. Verify MAC / tag
        if (decodeMac != null) {
            byte[] expectedMac;
            if (decodeMac.isEncryptThenMac()) {
                expectedMac = decodeMac.compute(inputSequenceNumber, encrypted);
            } else if (!decodeCipher.isAead()) {
                expectedMac = decodeMac.compute(inputSequenceNumber, packetBytes);
            } else {
                expectedMac = null;
            }
            if (expectedMac != null) {
                if (!constantTimeEquals(expectedMac, authData)) {
                    LOG.debug("[CODEC-MAC] MISMATCH seq=" + inputSequenceNumber
                        + " expected=" + bytesToHex(expectedMac, 16)
                        + " got=" + bytesToHex(authData, 16));
                    throw new IllegalArgumentException("MAC verification failed");
                }
            }
            LOG.debug("[CODEC-MAC] OK seq=" + inputSequenceNumber);
        }

        // 4. Track blocks for CTR counter sync
        int bs = decodeCipher != null ? decodeCipher.blockSize() : 16;
        if (bs < 8) bs = 8;
        blocksProcessed += (encrypted.length / bs);
        LOG.debug("[CODEC-DEC BLOCKS] seq=" + inputSequenceNumber
            + " encBlocks=" + (encrypted.length / bs) + " total=" + blocksProcessed);

        inputSequenceNumber++;

        // 5. Parse packet format
        // ChaCha20: [padLen:1][payload][padding] — no pktLen prefix
        // AES-GCM/AES-CTR/no-cipher: [pktLen:4][padLen:1][payload][padding]
        ByteBuffer buf = ByteBuffer.wrap(packetBytes);
        if (decodeCipher != null && decodeCipher.isPayloadOnly()) {
            // ChaCha20: parse payload-only format
            int paddingLength = buf.get() & 0xFF;
            int payloadLength = packetBytes.length - 1 - paddingLength;
            LOG.debug("[CODEC-DEC] ChaCha20 padLen=" + paddingLength + " payloadLen=" + payloadLength);
            if (payloadLength < 0 || payloadLength > MAX_PACKET_SIZE) {
                throw new IllegalArgumentException("Invalid payload length: " + payloadLength);
            }
            byte[] payload = new byte[payloadLength];
            buf.get(payload);
            if (compression != null) payload = compression.decompress(payload);
            LOG.debug("[CODEC-DEC] ChaCha20 payloadLen=" + payload.length + " msgType="
                + (payload.length > 0 ? (payload[0] & 0xFF) : -1));
            return payload;
        } else {
            // Standard: parse packet format
            // AES-CTR (cipher present): packetBytes includes pktLen prefix
            // No-cipher: packetBytes starts with padLen, wirePktLen from prefix
            int packetLength;
            int paddingLength;
            if (decodeCipher != null) {
                // AES-CTR: pktLen inside decrypted packet data
                packetLength = buf.getInt();
                paddingLength = buf.get() & 0xFF;
            } else {
                // No-cipher: use wire pktLen (prefix), read padLen next
                packetLength = wirePktLen;
                paddingLength = buf.get() & 0xFF;
            }
            int payloadLength = packetLength - paddingLength - 1;
            LOG.debug("[CODEC-DEC] pktLen=" + packetLength + " padLen=" + paddingLength + " payloadLen=" + payloadLength);
            if (payloadLength < 0 || payloadLength > MAX_PACKET_SIZE) {
                throw new IllegalArgumentException("Invalid payload length: " + payloadLength);
            }
            byte[] payload = new byte[payloadLength];
            buf.get(payload);
            if (compression != null) payload = compression.decompress(payload);
            LOG.debug("[CODEC-DEC] payloadLen=" + payload.length + " msgType="
                + (payload.length > 0 ? (payload[0] & 0xFF) : -1));
            return payload;
        }
    }

    public static String readString(ByteBuffer buf) {
        int length = buf.getInt();
        if (length < 0 || length > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Invalid string length: " + length);
        }
        byte[] data = new byte[length];
        buf.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    public static void writeString(ByteBuffer buf, String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        buf.putInt(data.length);
        buf.put(data);
    }

    public static byte[] readBinary(ByteBuffer buf) {
        int length = buf.getInt();
        if (length < 0 || length > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Invalid binary data length: " + length);
        }
        byte[] data = new byte[length];
        buf.get(data);
        return data;
    }

    public static void writeBinary(ByteBuffer buf, byte[] data) {
        buf.putInt(data.length);
        buf.put(data);
    }

    public static List<String> readNameList(ByteBuffer buf) {
        String nl = readString(buf);
        if (nl.isEmpty()) return List.of();
        return List.of(nl.split(","));
    }

    public static void writeNameList(ByteBuffer buf, List<String> names) {
        writeString(buf, String.join(",", names));
    }

    public static boolean readBoolean(ByteBuffer buf) {
        return buf.get() != 0;
    }

    public static void writeBoolean(ByteBuffer buf, boolean value) {
        buf.put(value ? (byte) 1 : (byte) 0);
    }

    public static long readUint32(ByteBuffer buf) {
        return buf.getInt() & 0xFFFFFFFFL;
    }

    public static void writeUint32(ByteBuffer buf, long value) {
        buf.putInt((int) (value & 0xFFFFFFFFL));
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }

    public void resetSequenceNumbers() {
        this.inputSequenceNumber = 0;
        this.outputSequenceNumber = 0;
        this.blocksProcessed = 0;
    }

    private static String bytesToHex(byte[] data, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(data.length, maxBytes);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x ", data[i]));
        }
        return sb.toString().trim();
    }
}
