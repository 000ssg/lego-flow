package ssg.legoflow.ssh.transport;

import ssg.legoflow.ssh.cipher.SshCipher;
import ssg.legoflow.ssh.mac.SshMac;
import ssg.legoflow.ssh.compression.SshCompression;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * SSH binary packet encoding and decoding per RFC 4253 section 6.
 *
 * <p>The binary packet format is:
 * <pre>
 *   uint32    packet_length
 *   byte      padding_length
 *   byte[n1]  payload; n1 = packet_length - padding_length - 1
 *   byte[n2]  random padding; n2 = padding_length
 *   byte[m]   mac (Message Authentication Code)
 * </pre>
 *
 * <p>Before encryption, packet_length is cleartext. After encryption,
 * everything is encrypted (unless using AEAD ciphers like GCM).
 *
 * @since 0.1.0
 */
public final class SshTransportCodec {

    /** Maximum allowed packet size per RFC 4253 (35000 bytes). */
    public static final int MAX_PACKET_SIZE = 35000;

    /** Minimum padding length per RFC 4253. */
    public static final int MIN_PADDING = 4;

    /** Maximum padding length. */
    public static final int MAX_PADDING = 255;

    private final SecureRandom random;
    private SshCipher cipher;
    private SshMac mac;
    private SshCompression compression;
    private long inputSequenceNumber;
    private long outputSequenceNumber;

    /**
     * Creates a new transport codec with no encryption.
     */
    public SshTransportCodec() {
        this.random = new SecureRandom();
        this.inputSequenceNumber = 0;
        this.outputSequenceNumber = 0;
    }

    /**
     * Sets the cipher for encryption/decryption.
     *
     * @param cipher the cipher to use, or null for no encryption
     */
    public void setCipher(SshCipher cipher) {
        this.cipher = cipher;
    }

    /**
     * Sets the MAC algorithm.
     *
     * @param mac the MAC algorithm, or null for no MAC
     */
    public void setMac(SshMac mac) {
        this.mac = mac;
    }

    /**
     * Sets the compression algorithm.
     *
     * @param compression the compression algorithm, or null for no compression
     */
    public void setCompression(SshCompression compression) {
        this.compression = compression;
    }

    /**
     * Returns the current output sequence number.
     *
     * @return the output sequence number
     */
    public long outputSequenceNumber() {
        return outputSequenceNumber;
    }

    /**
     * Returns the current input sequence number.
     *
     * @return the input sequence number
     */
    public long inputSequenceNumber() {
        return inputSequenceNumber;
    }

    /**
     * Encodes a payload into an SSH binary packet.
     *
     * @param payload the payload bytes (message type + message data)
     * @return the encoded packet ready for transmission
     * @throws IllegalArgumentException if the payload exceeds maximum size
     */
    public byte[] encode(byte[] payload) {
        Objects.requireNonNull(payload, "payload");

        // Compress if enabled
        byte[] compressedPayload = payload;
        if (compression != null) {
            compressedPayload = compression.compress(payload);
        }

        int blockSize = cipher != null ? cipher.blockSize() : 8;
        if (blockSize < 8) blockSize = 8;

        // Calculate padding: packet_length + padding_length + payload must be multiple of blockSize
        // minimum 4 bytes of padding
        int packetLengthFieldSize = 4;
        int paddingLengthFieldSize = 1;
        int unpadded = paddingLengthFieldSize + compressedPayload.length;
        int paddingLength = blockSize - ((unpadded + packetLengthFieldSize) % blockSize);
        if (paddingLength < MIN_PADDING) {
            paddingLength += blockSize;
        }

        int packetLength = paddingLengthFieldSize + compressedPayload.length + paddingLength;

        if (packetLength + packetLengthFieldSize > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Packet too large: " + (packetLength + packetLengthFieldSize));
        }

        // Build the packet
        ByteBuffer packet = ByteBuffer.allocate(packetLengthFieldSize + packetLength);
        packet.putInt(packetLength);
        packet.put((byte) paddingLength);
        packet.put(compressedPayload);

        // Random padding
        byte[] padding = new byte[paddingLength];
        random.nextBytes(padding);
        packet.put(padding);

        byte[] packetBytes = packet.array();

        // Compute MAC before encryption (for non-ETM modes)
        byte[] macBytes = new byte[0];
        if (mac != null && !mac.isEncryptThenMac()) {
            macBytes = mac.compute(outputSequenceNumber, packetBytes);
        }

        // Encrypt
        byte[] encrypted;
        if (cipher != null) {
            encrypted = cipher.encrypt(packetBytes);
        } else {
            encrypted = packetBytes;
        }

        // Compute MAC after encryption (for ETM modes)
        if (mac != null && mac.isEncryptThenMac()) {
            macBytes = mac.compute(outputSequenceNumber, encrypted);
        }

        outputSequenceNumber++;

        // Concatenate encrypted packet + MAC
        byte[] result = new byte[encrypted.length + macBytes.length];
        System.arraycopy(encrypted, 0, result, 0, encrypted.length);
        System.arraycopy(macBytes, 0, result, encrypted.length, macBytes.length);

        return result;
    }

    /**
     * Decodes an SSH binary packet from raw bytes.
     *
     * @param data the raw bytes including packet_length, payload, padding, and MAC
     * @return the decoded payload bytes
     * @throws IllegalArgumentException if the packet is malformed
     */
    public byte[] decode(byte[] data) {
        Objects.requireNonNull(data, "data");

        int macLength = mac != null ? mac.macLength() : 0;

        byte[] packetBytes;
        byte[] macBytes;

        if (mac != null && mac.isEncryptThenMac()) {
            // For ETM: verify MAC on encrypted data, then decrypt
            int encLen = data.length - macLength;
            byte[] encrypted = Arrays.copyOfRange(data, 0, encLen);
            macBytes = Arrays.copyOfRange(data, encLen, data.length);

            byte[] expectedMac = mac.compute(inputSequenceNumber, encrypted);
            if (!constantTimeEquals(expectedMac, macBytes)) {
                throw new IllegalArgumentException("MAC verification failed");
            }

            packetBytes = cipher != null ? cipher.decrypt(encrypted) : encrypted;
        } else {
            // For non-ETM: decrypt, then verify MAC
            int encLen = data.length - macLength;
            byte[] encrypted = Arrays.copyOfRange(data, 0, encLen);
            macBytes = macLength > 0 ? Arrays.copyOfRange(data, encLen, data.length) : new byte[0];

            packetBytes = cipher != null ? cipher.decrypt(encrypted) : encrypted;

            if (mac != null) {
                byte[] expectedMac = mac.compute(inputSequenceNumber, packetBytes);
                if (!constantTimeEquals(expectedMac, macBytes)) {
                    throw new IllegalArgumentException("MAC verification failed");
                }
            }
        }

        inputSequenceNumber++;

        // Parse the decrypted packet
        ByteBuffer buf = ByteBuffer.wrap(packetBytes);
        int packetLength = buf.getInt();
        int paddingLength = buf.get() & 0xFF;
        int payloadLength = packetLength - paddingLength - 1;

        if (payloadLength < 0 || payloadLength > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Invalid payload length: " + payloadLength);
        }

        byte[] payload = new byte[payloadLength];
        buf.get(payload);

        // Decompress if enabled
        if (compression != null) {
            payload = compression.decompress(payload);
        }

        return payload;
    }

    /**
     * Reads SSH string (uint32 length + data) from a ByteBuffer.
     *
     * @param buf the buffer to read from
     * @return the string value
     */
    public static String readString(ByteBuffer buf) {
        int length = buf.getInt();
        if (length < 0 || length > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Invalid string length: " + length);
        }
        byte[] data = new byte[length];
        buf.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Writes an SSH string (uint32 length + data) to a ByteBuffer.
     *
     * @param buf   the buffer to write to
     * @param value the string value
     */
    public static void writeString(ByteBuffer buf, String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        buf.putInt(data.length);
        buf.put(data);
    }

    /**
     * Reads SSH binary data (uint32 length + data) from a ByteBuffer.
     *
     * @param buf the buffer to read from
     * @return the binary data
     */
    public static byte[] readBinary(ByteBuffer buf) {
        int length = buf.getInt();
        if (length < 0 || length > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Invalid binary data length: " + length);
        }
        byte[] data = new byte[length];
        buf.get(data);
        return data;
    }

    /**
     * Writes SSH binary data (uint32 length + data) to a ByteBuffer.
     *
     * @param buf  the buffer to write to
     * @param data the binary data
     */
    public static void writeBinary(ByteBuffer buf, byte[] data) {
        buf.putInt(data.length);
        buf.put(data);
    }

    /**
     * Reads a name-list (comma-separated) from a ByteBuffer.
     *
     * @param buf the buffer to read from
     * @return the list of names
     */
    public static List<String> readNameList(ByteBuffer buf) {
        String nameList = readString(buf);
        if (nameList.isEmpty()) {
            return List.of();
        }
        return List.of(nameList.split(","));
    }

    /**
     * Writes a name-list (comma-separated) to a ByteBuffer.
     *
     * @param buf   the buffer to write to
     * @param names the list of names
     */
    public static void writeNameList(ByteBuffer buf, List<String> names) {
        writeString(buf, String.join(",", names));
    }

    /**
     * Reads a boolean from a ByteBuffer.
     *
     * @param buf the buffer to read from
     * @return the boolean value
     */
    public static boolean readBoolean(ByteBuffer buf) {
        return buf.get() != 0;
    }

    /**
     * Writes a boolean to a ByteBuffer.
     *
     * @param buf   the buffer to write to
     * @param value the boolean value
     */
    public static void writeBoolean(ByteBuffer buf, boolean value) {
        buf.put(value ? (byte) 1 : (byte) 0);
    }

    /**
     * Reads an unsigned 32-bit integer from a ByteBuffer.
     *
     * @param buf the buffer to read from
     * @return the value as a long
     */
    public static long readUint32(ByteBuffer buf) {
        return buf.getInt() & 0xFFFFFFFFL;
    }

    /**
     * Writes an unsigned 32-bit integer to a ByteBuffer.
     *
     * @param buf   the buffer to write to
     * @param value the value (must be in range 0 to 2^32-1)
     */
    public static void writeUint32(ByteBuffer buf, long value) {
        buf.putInt((int) (value & 0xFFFFFFFFL));
    }

    /**
     * Constant-time byte array comparison to prevent timing attacks.
     *
     * @param a first array
     * @param b second array
     * @return true if arrays are equal
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * Resets the sequence numbers (used during key re-exchange).
     */
    public void resetSequenceNumbers() {
        this.inputSequenceNumber = 0;
        this.outputSequenceNumber = 0;
    }
}
