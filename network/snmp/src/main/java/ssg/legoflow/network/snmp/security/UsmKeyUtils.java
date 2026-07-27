package ssg.legoflow.network.snmp.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * USM key localization utilities as defined in RFC 3414 Section A.2.
 *
 * <p>Implements the password-to-key and key localization algorithms
 * required for SNMPv3 USM authentication and privacy.
 *
 * <p>This class is stateless and thread-safe.
 *
 * @since 1.0.0
 */
public final class UsmKeyUtils {

    /** The constant 1,048,576 (1 MB) used in password-to-key derivation. */
    private static final int PASSWORD_EXPANSION_LENGTH = 1_048_576;

    private UsmKeyUtils() {}

    /**
     * Converts a password to a master key using the password-to-key algorithm
     * defined in RFC 3414 Section A.2.1.
     *
     * <p>The password is cyclically repeated to fill 1 MB of data, then hashed
     * with the specified algorithm (MD5 or SHA-1).
     *
     * @param password      the user password
     * @param hashAlgorithm the hash algorithm ("MD5" or "SHA-1")
     * @return the master key (16 bytes for MD5, 20 bytes for SHA-1)
     */
    public static byte[] passwordToKey(String password, String hashAlgorithm) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be null or empty");
        }
        try {
            MessageDigest md = MessageDigest.getInstance(hashAlgorithm);
            byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
            int passwordLen = passwordBytes.length;

            int count = 0;
            int index = 0;
            byte[] block = new byte[64];

            while (count < PASSWORD_EXPANSION_LENGTH) {
                for (int i = 0; i < 64; i++) {
                    block[i] = passwordBytes[index % passwordLen];
                    index++;
                }
                md.update(block);
                count += 64;
            }

            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new UsmSecurityException("Hash algorithm not available: " + hashAlgorithm, e);
        }
    }

    /**
     * Localizes a master key with an engine ID using the key localization
     * algorithm defined in RFC 3414 Section A.2.2.
     *
     * <p>Localized key = Hash(masterKey + engineID + masterKey)
     *
     * @param masterKey     the master key from passwordToKey
     * @param engineId      the authoritative engine ID
     * @param hashAlgorithm the hash algorithm ("MD5" or "SHA-1")
     * @return the localized key
     */
    public static byte[] localizeKey(byte[] masterKey, byte[] engineId, String hashAlgorithm) {
        if (masterKey == null) throw new IllegalArgumentException("Master key must not be null");
        if (engineId == null) throw new IllegalArgumentException("Engine ID must not be null");
        try {
            MessageDigest md = MessageDigest.getInstance(hashAlgorithm);
            md.update(masterKey);
            md.update(engineId);
            md.update(masterKey);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new UsmSecurityException("Hash algorithm not available: " + hashAlgorithm, e);
        }
    }

    /**
     * Derives a localized key from a password and engine ID in one step.
     *
     * <p>This combines passwordToKey and localizeKey for convenience.
     *
     * @param password     the user password
     * @param engineId     the authoritative engine ID
     * @param authProtocol the authentication protocol (determines hash algorithm)
     * @return the localized key
     */
    public static byte[] deriveLocalizedKey(String password, byte[] engineId,
                                             AuthProtocol authProtocol) {
        if (authProtocol == AuthProtocol.NONE) {
            return new byte[0];
        }
        byte[] masterKey = passwordToKey(password, authProtocol.hashAlgorithm());
        return localizeKey(masterKey, engineId, authProtocol.hashAlgorithm());
    }

    /**
     * Derives the privacy localized key from a password and engine ID.
     *
     * <p>The privacy key is derived using the same password-to-key and
     * localization process as the auth key, using the auth protocol's hash.
     *
     * @param password     the privacy password
     * @param engineId     the authoritative engine ID
     * @param authProtocol the authentication protocol (determines hash algorithm)
     * @param privProtocol the privacy protocol (determines key length)
     * @return the localized privacy key, truncated to privProtocol.keyLength()
     */
    public static byte[] derivePrivLocalizedKey(String password, byte[] engineId,
                                                 AuthProtocol authProtocol,
                                                 PrivProtocol privProtocol) {
        if (privProtocol == PrivProtocol.NONE) {
            return new byte[0];
        }
        byte[] localizedKey = deriveLocalizedKey(password, engineId, authProtocol);
        if (localizedKey.length >= privProtocol.keyLength()) {
            return Arrays.copyOf(localizedKey, privProtocol.keyLength());
        }
        return localizedKey;
    }
}
