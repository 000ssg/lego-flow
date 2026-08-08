package ssg.legoflow.network.snmp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.snmp.protocol.SnmpCodec;
import ssg.legoflow.network.snmp.protocol.SnmpMessage;
import ssg.legoflow.network.snmp.protocol.SecurityLevel;
import ssg.legoflow.network.snmp.protocol.UsmSecurityParameters;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * USM (User-based Security Model) security engine as defined in RFC 3414.
 *
 * <p>Provides authentication (HMAC-MD5-96, HMAC-SHA-96) and privacy
 * (DES-CBC, AES-128-CFB) services for SNMPv3 messages. Manages
 * engine timing for replay protection and user credentials.
 *
 * <p>This class is thread-safe.
 *
 * @since 0.1.0
 */
public final class UsmEngine {

    private static final Logger LOG = LoggerFactory.getLogger(UsmEngine.class);

    private final byte[] engineId;
    private final AtomicInteger engineBoots;
    private final AtomicInteger engineTime;
    private final long startTimeMillis;
    private final ConcurrentHashMap<String, UsmUser> users = new ConcurrentHashMap<>();
    private final AtomicInteger saltCounter = new AtomicInteger(0);

    /**
     * Creates a USM engine with the given engine ID.
     *
     * @param engineId    the unique engine identifier
     * @param engineBoots the initial engine boots counter
     */
    public UsmEngine(byte[] engineId, int engineBoots) {
        if (engineId == null || engineId.length == 0) {
            throw new IllegalArgumentException("Engine ID must not be null or empty");
        }
        this.engineId = engineId.clone();
        this.engineBoots = new AtomicInteger(engineBoots);
        this.engineTime = new AtomicInteger(0);
        this.startTimeMillis = System.currentTimeMillis();
    }

    /**
     * Creates a USM engine with the given engine ID and initial boots of 0.
     *
     * @param engineId the unique engine identifier
     */
    public UsmEngine(byte[] engineId) {
        this(engineId, 0);
    }

    /**
     * Returns the engine ID.
     *
     * @return copy of the engine ID
     */
    public byte[] engineId() {
        return engineId.clone();
    }

    /**
     * Returns the current engine boots value.
     *
     * @return the engine boots
     */
    public int engineBoots() {
        return engineBoots.get();
    }

    /**
     * Returns the current engine time in seconds since last reboot.
     *
     * @return the engine time
     */
    public int engineTime() {
        long elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000;
        return (int) elapsed;
    }

    /**
     * Adds a user to this engine.
     *
     * @param user the USM user
     */
    public void addUser(UsmUser user) {
        users.put(user.userName(), user);
        LOG.debug("Added USM user: {}", user.userName());
    }

    /**
     * Returns the user with the given name.
     *
     * @param userName the user name
     * @return the user, or null if not found
     */
    public UsmUser getUser(String userName) {
        return users.get(userName);
    }

    /**
     * Removes a user from this engine.
     *
     * @param userName the user name
     */
    public void removeUser(String userName) {
        users.remove(userName);
    }

    // ── Authentication ──

    /**
     * Computes the HMAC authentication digest for an SNMP message.
     *
     * <p>The authentication parameters field in the message must be set to
     * 12 zero bytes before computing the HMAC. After computation, the first
     * 12 bytes of the HMAC are placed in the authParams field.
     *
     * @param messageBytes the complete BER-encoded message with zeroed authParams
     * @param user         the USM user with auth credentials
     * @return the 12-byte truncated HMAC digest
     */
    public byte[] computeAuth(byte[] messageBytes, UsmUser user) {
        if (user.authProtocol() == AuthProtocol.NONE) {
            return new byte[0];
        }
        try {
            Mac mac = Mac.getInstance(user.authProtocol().algorithm());
            SecretKeySpec keySpec = new SecretKeySpec(user.authKey(), user.authProtocol().algorithm());
            mac.init(keySpec);
            byte[] fullDigest = mac.doFinal(messageBytes);
            return Arrays.copyOf(fullDigest, user.authProtocol().truncatedLength());
        } catch (GeneralSecurityException e) {
            throw new UsmSecurityException("Authentication computation failed", e);
        }
    }

    /**
     * Verifies the authentication digest on a received message.
     *
     * @param messageBytes the complete BER-encoded message
     * @param authParams   the received authentication parameters
     * @param user         the USM user
     * @return true if the digest matches
     */
    public boolean verifyAuth(byte[] messageBytes, byte[] authParams, UsmUser user) {
        if (user.authProtocol() == AuthProtocol.NONE) {
            return true;
        }
        byte[] computed = computeAuth(messageBytes, user);
        return Arrays.equals(computed, authParams);
    }

    // ── Privacy ──

    /**
     * Encrypts the scoped PDU data using the user's privacy protocol.
     *
     * @param scopedPduBytes the plaintext scoped PDU bytes
     * @param user           the USM user with privacy credentials
     * @param engineBoots    the engine boots value
     * @param engineTime     the engine time value
     * @return the encryption result containing ciphertext and privacy parameters
     */
    public EncryptionResult encrypt(byte[] scopedPduBytes, UsmUser user,
                                     int engineBoots, int engineTime) {
        if (user.privProtocol() == PrivProtocol.NONE) {
            return new EncryptionResult(scopedPduBytes, new byte[0]);
        }

        try {
            byte[] privKey = user.privKey();
            int salt = saltCounter.getAndIncrement();

            return switch (user.privProtocol()) {
                case DES_CBC -> encryptDes(scopedPduBytes, privKey, engineBoots, salt);
                case AES_128_CFB -> encryptAes(scopedPduBytes, privKey, engineBoots, engineTime, salt);
                case NONE -> throw new AssertionError("unreachable");
            };
        } catch (GeneralSecurityException e) {
            throw new UsmSecurityException("Encryption failed", e);
        }
    }

    /**
     * Decrypts the encrypted scoped PDU data.
     *
     * @param encryptedData the ciphertext
     * @param user          the USM user with privacy credentials
     * @param privParams    the privacy parameters from the message
     * @param engineBoots   the engine boots value
     * @param engineTime    the engine time value
     * @return the decrypted scoped PDU bytes
     */
    public byte[] decrypt(byte[] encryptedData, UsmUser user, byte[] privParams,
                          int engineBoots, int engineTime) {
        if (user.privProtocol() == PrivProtocol.NONE) {
            return encryptedData;
        }

        try {
            byte[] privKey = user.privKey();

            return switch (user.privProtocol()) {
                case DES_CBC -> decryptDes(encryptedData, privKey, privParams);
                case AES_128_CFB -> decryptAes(encryptedData, privKey, privParams,
                        engineBoots, engineTime);
                case NONE -> throw new AssertionError("unreachable");
            };
        } catch (GeneralSecurityException e) {
            throw new UsmSecurityException("Decryption failed", e);
        }
    }

    // ── DES-CBC (RFC 3414) ──

    private EncryptionResult encryptDes(byte[] data, byte[] privKey,
                                         int boots, int salt)
            throws GeneralSecurityException {
        // DES key is first 8 bytes of localized key
        byte[] desKey = Arrays.copyOf(privKey, 8);
        // Pre-IV is last 8 bytes of localized key
        byte[] preIv = Arrays.copyOfRange(privKey, 8, 16);

        // Salt (privacy parameters) is boots(4) + localSalt(4)
        byte[] privParams = new byte[8];
        privParams[0] = (byte) (boots >> 24);
        privParams[1] = (byte) (boots >> 16);
        privParams[2] = (byte) (boots >> 8);
        privParams[3] = (byte) boots;
        privParams[4] = (byte) (salt >> 24);
        privParams[5] = (byte) (salt >> 16);
        privParams[6] = (byte) (salt >> 8);
        privParams[7] = (byte) salt;

        // IV = preIv XOR salt
        byte[] iv = new byte[8];
        for (int i = 0; i < 8; i++) {
            iv[i] = (byte) (preIv[i] ^ privParams[i]);
        }

        // Pad to 8-byte boundary
        byte[] padded = padToBoundary(data, 8);

        Cipher cipher = Cipher.getInstance("DES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(desKey, "DES"),
                new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(padded);

        return new EncryptionResult(encrypted, privParams);
    }

    private byte[] decryptDes(byte[] data, byte[] privKey, byte[] privParams)
            throws GeneralSecurityException {
        byte[] desKey = Arrays.copyOf(privKey, 8);
        byte[] preIv = Arrays.copyOfRange(privKey, 8, 16);

        byte[] iv = new byte[8];
        for (int i = 0; i < 8; i++) {
            iv[i] = (byte) (preIv[i] ^ privParams[i]);
        }

        Cipher cipher = Cipher.getInstance("DES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(desKey, "DES"),
                new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    // ── AES-128-CFB (RFC 3826) ──

    private EncryptionResult encryptAes(byte[] data, byte[] privKey,
                                         int boots, int time, int salt)
            throws GeneralSecurityException {
        // Privacy parameters = local 64-bit salt
        byte[] privParams = new byte[8];
        privParams[0] = (byte) (salt >> 56);
        privParams[1] = (byte) (salt >> 48);
        privParams[2] = (byte) (salt >> 40);
        privParams[3] = (byte) (salt >> 32);
        privParams[4] = (byte) (salt >> 24);
        privParams[5] = (byte) (salt >> 16);
        privParams[6] = (byte) (salt >> 8);
        privParams[7] = (byte) salt;

        // IV = boots(4) + time(4) + privParams(8)
        byte[] iv = buildAesIv(boots, time, privParams);

        byte[] aesKey = Arrays.copyOf(privKey, 16);
        Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(data);

        return new EncryptionResult(encrypted, privParams);
    }

    private byte[] decryptAes(byte[] data, byte[] privKey, byte[] privParams,
                               int boots, int time)
            throws GeneralSecurityException {
        byte[] iv = buildAesIv(boots, time, privParams);

        byte[] aesKey = Arrays.copyOf(privKey, 16);
        Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    private byte[] buildAesIv(int boots, int time, byte[] privParams) {
        byte[] iv = new byte[16];
        iv[0] = (byte) (boots >> 24);
        iv[1] = (byte) (boots >> 16);
        iv[2] = (byte) (boots >> 8);
        iv[3] = (byte) boots;
        iv[4] = (byte) (time >> 24);
        iv[5] = (byte) (time >> 16);
        iv[6] = (byte) (time >> 8);
        iv[7] = (byte) time;
        System.arraycopy(privParams, 0, iv, 8, 8);
        return iv;
    }

    private byte[] padToBoundary(byte[] data, int boundary) {
        int remainder = data.length % boundary;
        if (remainder == 0) return data;
        int padded = data.length + (boundary - remainder);
        return Arrays.copyOf(data, padded);
    }

    /**
     * Result of an encryption operation.
     *
     * @param encryptedData the ciphertext
     * @param privParams    the privacy parameters (salt/IV information)
     * @since 0.1.0
     */
    public record EncryptionResult(byte[] encryptedData, byte[] privParams) {
        /**
         * Creates an encryption result with defensive copies.
         */
        public EncryptionResult {
            encryptedData = encryptedData.clone();
            privParams = privParams.clone();
        }

        @Override
        public byte[] encryptedData() { return encryptedData.clone(); }

        @Override
        public byte[] privParams() { return privParams.clone(); }
    }
}
