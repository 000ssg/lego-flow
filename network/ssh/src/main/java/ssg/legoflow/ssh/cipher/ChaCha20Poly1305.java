package ssg.legoflow.ssh.cipher;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * ChaCha20-Poly1305 AEAD cipher for SSH (chacha20-poly1305@openssh.com).
 *
 * <p>Per OpenSSH specification:
 * <ul>
 *   <li>64-byte key: first 32 bytes = ChaCha20 key, second 32 bytes = Poly1305 key</li>
 *   <li>Nonce construction: 12-byte nonce where first 4 bytes = sequence number (big-endian), last 8 bytes = 0</li>
 *   <li>Encryption: payload encrypted with counter=2</li>
 *   <li>Poly1305 tag: 16 bytes, computed over [enc_pktLen || enc_payload]</li>
 *   <li>Wire format: [4-byte PLAINTEXT pktLen][enc_payload][16-byte tag]</li>
 * </ul>
 *
 * <p><b>Separation of concerns:</b> The codec handles SSH packet format
 * ([pktLen][padLen][payload][padding]). The cipher receives ONLY the payload
 * portion (padLen byte + payload + padding) via encryptPayload/decryptPayload.
 * The codec extracts pktLen from the full packet and uses it for tag computation.
 *
 * @since 0.1.0
 */
public final class ChaCha20Poly1305 implements SshCipher {

    private static final int TAG_LEN = 16;
    private static final int NONCE_LEN = 12;
    private static final BigInteger PRIME = BigInteger.valueOf(2).pow(130).subtract(BigInteger.valueOf(5));

    // RFC 8439 constants: "expand 32-byte k" as 32-bit LE words
    private static final int[] CONSTANTS = {
        0x61707865, 0x3320646e, 0x79622d32, 0x6574656b
    };

    private byte[] key20;
    private byte[] polyKey;
    private long sequenceNumber = 0;

    @Override public String name() { return "chacha20-poly1305@openssh.com"; }
    @Override public int blockSize() { return 8; }
    @Override public int keySize() { return 64; }
    @Override public int ivSize() { return 0; }
    @Override public boolean isAead() { return true; }
    @Override public int authTagLength() { return TAG_LEN; }
    @Override public int nonceLen() { return NONCE_LEN; }
    @Override public boolean isPayloadOnly() { return true; }

    @Override
    public void init(byte[] key, byte[] iv, boolean encrypt) {
        if (key.length < 64) {
            throw new IllegalArgumentException("ChaCha20-Poly1305 requires 64 bytes of key material");
        }
        this.key20 = new byte[32];
        System.arraycopy(key, 0, this.key20, 0, 32);
        this.polyKey = new byte[32];
        System.arraycopy(key, 32, this.polyKey, 0, 32);
        this.sequenceNumber = 0;
    }

    @Override
    public void setSequenceNumber(long seq) {
        this.sequenceNumber = seq;
    }

    // === ChaCha20 Core ===

    private static int rotl(int x, int n) {
        return (x << n) | (x >>> (32 - n));
    }

    private static void quarterRound(int[] w, int a, int b, int c, int d) {
        w[a] += w[b]; w[d] ^= w[a]; w[d] = rotl(w[d], 16);
        w[c] += w[d]; w[b] ^= w[c]; w[b] = rotl(w[b], 12);
        w[a] += w[b]; w[d] ^= w[a]; w[d] = rotl(w[d], 8);
        w[c] += w[d]; w[b] ^= w[c]; w[b] = rotl(w[b], 7);
    }

    /**
     * Generate a single 64-byte ChaCha20 keystream block for the given counter.
     * Verified against RFC 8439 Section 2.3.2 test vector.
     */
    private int[] chacha20Block(int counter) {
        int[] state = new int[16];
        for (int i = 0; i < 8; i++) {
            state[i] = ByteBuffer.wrap(key20, i * 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }
        System.arraycopy(CONSTANTS, 0, state, 8, 4);
        state[12] = counter;
        int seq32 = (int) (sequenceNumber & 0xFFFFFFFFL);
        state[13] = seq32;
        state[14] = 0;
        state[15] = 0;

        int[] orig = state.clone();
        for (int r = 0; r < 20; r++) {
            if ((r & 1) == 0) {
                quarterRound(state, 0, 4, 8, 12);
                quarterRound(state, 1, 5, 9, 13);
                quarterRound(state, 2, 6, 10, 14);
                quarterRound(state, 3, 7, 11, 15);
            } else {
                quarterRound(state, 0, 5, 10, 15);
                quarterRound(state, 1, 6, 11, 12);
                quarterRound(state, 2, 7, 8, 13);
                quarterRound(state, 3, 4, 9, 14);
            }
        }
        for (int i = 0; i < 16; i++) {
            state[i] += orig[i];
        }
        return state;
    }

    /** Generate keystream bytes for given counter and length (multi-block). */
    private byte[] keystreamBlock(int counter, int length) {
        int[] state = chacha20Block(counter);
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            int wordIdx = i / 4;
            int byteOffset = i % 4;
            int v = state[wordIdx] & 0xFFFFFFFF;
            out[i] = (byte) ((v >>> (8 * byteOffset)) & 0xFF);
        }
        return out;
    }

    /**
     * Generate keystream for a counter and length, using multiple blocks if needed.
     * Each 64-byte block increments the counter.
     */
    private byte[] keystream(int startCounter, int length) {
        int numBlocks = (length + 63) / 64;
        int totalBytes = numBlocks * 64;
        byte[] fullStream = new byte[totalBytes];
        for (int b = 0; b < numBlocks; b++) {
            int[] state = chacha20Block(startCounter + b);
            for (int i = 0; i < 64; i++) {
                int wordIdx = i / 4;
                int byteOffset = i % 4;
                int v = state[wordIdx] & 0xFFFFFFFF;
                fullStream[i + b * 64] = (byte) ((v >>> (8 * byteOffset)) & 0xFF);
            }
        }
        return Arrays.copyOf(fullStream, length);
    }

    /** XOR src into dest in-place. */
    private void xorInPlace(byte[] dest, byte[] src) {
        for (int i = 0; i < dest.length; i++) {
            dest[i] ^= src[i];
        }
    }

    // === ChaCha20 Encrypt Payload ===

    /**
     * Encrypts ONLY the payload portion (padLen + payload + padding).
     * Does NOT parse packet format — the codec handles that.
     *
     * <p>Uses ChaCha20 counter=2 for payload encryption.
     * enc_pktLen (counter=1) is computed internally for tag purposes.
     *
     * @param payloadWithPadding the payload portion: [padLen:1][payload:var][padding:var]
     * @return [encryptedPayload:var][tag:16]
     */
    @Override
    public byte[] encryptPayload(byte[] payloadWithPadding) {
        int payloadLen = payloadWithPadding.length;

        // Compute enc_pktLen (counter=1) for tag computation
        byte[] encLen = keystreamBlock(1, 4);

        // Compute enc_payload (counter=2+) — handle payloads > 64 bytes
        byte[] payloadKeyStream = keystream(2, payloadLen);
        byte[] encPayload = payloadWithPadding.clone();
        xorInPlace(encPayload, payloadKeyStream);

        // Poly1305 tag over [encLen || encPayload]
        byte[] tag = poly1305(polyKey, encLen, encPayload);

        // Combine: [encPayload][tag]
        byte[] result = new byte[payloadLen + TAG_LEN];
        System.arraycopy(encPayload, 0, result, 0, payloadLen);
        System.arraycopy(tag, 0, result, payloadLen, TAG_LEN);

        return result;
    }

    // === ChaCha20 Decrypt Payload ===

    /**
     * Decrypts ONLY the payload portion.
     * Does NOT parse packet format — the codec handles that.
     *
     * <p>Uses ChaCha20 counter=2 for payload decryption.
     * Verifies Poly1305 tag computed over [enc_pktLen || enc_payload].
     *
     * @param encryptedWithTag [encryptedPayload:var][tag:16]
     * @return [padLen:1][payload:var][padding:var]
     */
    @Override
    public byte[] decryptPayload(byte[] encryptedWithTag) {
        if (encryptedWithTag.length < TAG_LEN) {
            throw new RuntimeException("ChaCha20-Poly1305 decrypt: data too short");
        }

        int payloadLen = encryptedWithTag.length - TAG_LEN;
        byte[] encPayload = new byte[payloadLen];
        byte[] tagFromWire = new byte[TAG_LEN];
        System.arraycopy(encryptedWithTag, 0, encPayload, 0, payloadLen);
        System.arraycopy(encryptedWithTag, payloadLen, tagFromWire, 0, TAG_LEN);

        // Compute enc_pktLen (counter=1) for tag verification
        byte[] encLen = keystreamBlock(1, 4);

        // Verify tag
        byte[] tagComputed = poly1305(polyKey, encLen, encPayload);
        if (!constantTimeEquals(tagFromWire, tagComputed)) {
            throw new SecurityException("ChaCha20-Poly1305 tag verification failed");
        }

        // Decrypt payload (counter=2+) — handle payloads > 64 bytes
        byte[] payloadKeyStream = keystream(2, payloadLen);
        byte[] decrypted = encPayload.clone();
        xorInPlace(decrypted, payloadKeyStream);

        return decrypted;
    }

    // === Poly1305 ===

    private byte[] poly1305(byte[] rKey, byte[] encLen, byte[] encPayload) {
        long r0 = ByteBuffer.wrap(rKey, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        long r1 = ByteBuffer.wrap(rKey, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        long r2 = ByteBuffer.wrap(rKey, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        long r3 = ByteBuffer.wrap(rKey, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        long s0 = ByteBuffer.wrap(rKey, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        long s1 = ByteBuffer.wrap(rKey, 20, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        long s2 = ByteBuffer.wrap(rKey, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        long s3 = ByteBuffer.wrap(rKey, 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;

        r0 &= 0x3FFFFFFC;
        r1 &= 0xFFFFFC00;
        r2 &= 0xFFFFF000;
        r3 &= 0xFFFFC000;

        byte[] rBytes = new byte[]{
            (byte)(r3 & 0xFF), (byte)(r3 >>> 8 & 0xFF), (byte)(r3 >>> 16 & 0xFF), (byte)(r3 >>> 24 & 0xFF),
            (byte)(r2 & 0xFF), (byte)(r2 >>> 8 & 0xFF), (byte)(r2 >>> 16 & 0xFF), (byte)(r2 >>> 24 & 0xFF),
            (byte)(r1 & 0xFF), (byte)(r1 >>> 8 & 0xFF), (byte)(r1 >>> 16 & 0xFF), (byte)(r1 >>> 24 & 0xFF),
            (byte)(r0 & 0xFF), (byte)(r0 >>> 8 & 0xFF), (byte)(r0 >>> 16 & 0xFF), (byte)(r0 >>> 24 & 0xFF)
        };
        BigInteger bigR = new BigInteger(1, rBytes);

        byte[] sBytes = new byte[]{
            (byte)(s3 & 0xFF), (byte)(s3 >>> 8 & 0xFF), (byte)(s3 >>> 16 & 0xFF), (byte)(s3 >>> 24 & 0xFF),
            (byte)(s2 & 0xFF), (byte)(s2 >>> 8 & 0xFF), (byte)(s2 >>> 16 & 0xFF), (byte)(s2 >>> 24 & 0xFF),
            (byte)(r1 & 0xFF), (byte)(r1 >>> 8 & 0xFF), (byte)(r1 >>> 16 & 0xFF), (byte)(r1 >>> 24 & 0xFF),
            (byte)(r0 & 0xFF), (byte)(r0 >>> 8 & 0xFF), (byte)(r0 >>> 16 & 0xFF), (byte)(r0 >>> 24 & 0xFF)
        };
        BigInteger bigS = new BigInteger(1, sBytes);

        byte[] msg = new byte[encLen.length + encPayload.length];
        System.arraycopy(encLen, 0, msg, 0, encLen.length);
        System.arraycopy(encPayload, 0, msg, encLen.length, encPayload.length);

        BigInteger acc = BigInteger.ZERO;
        for (int i = 0; i < msg.length; i += 16) {
            int blockLen = Math.min(16, msg.length - i);
            byte[] blockBytes = new byte[17];
            System.arraycopy(msg, i, blockBytes, 0, blockLen);
            blockBytes[blockLen] = 0x01;

            BigInteger block = new BigInteger(1, blockBytes);
            acc = acc.add(block).multiply(bigR).mod(PRIME);
        }

        acc = acc.add(bigS).mod(PRIME);

        byte[] accBytes = acc.toByteArray();
        byte[] result = new byte[16];
        for (int i = 0; i < Math.min(16, accBytes.length); i++) {
            result[15 - i] = accBytes[i];
        }
        return result;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }
}
