package ssg.legoflow.http.auth.saml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
/**
 * Decrypts SAML 2.0 EncryptedAssertion elements using the SP's private key.
 * Supports RSA-OAEP key unwrap with AES-128-CBC or AES-256-CBC content encryption.
 *
 * <p>The encrypted assertion structure follows SAML 2.0 and XML Encryption (xmlenc) standards:</p>
 * <ul>
 *   <li>{@code <EncryptedData>} contains the encrypted assertion</li>
 *   <li>{@code <EncryptedKey>} contains the AES key encrypted with RSA-OAEP</li>
 *   <li>{@code <CipherValue>} contains the Base64-encoded ciphertext</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class SamlEncryptedAssertion {

    private static final Logger LOG = LoggerFactory.getLogger(SamlEncryptedAssertion.class);

    private final PrivateKey privateKey;

    /**
     * Creates an encrypted assertion handler with the SP's private key.
     *
     * @param privateKey the SP's RSA private key for key unwrapping
     * @since 0.1.0
     */
    public SamlEncryptedAssertion(PrivateKey privateKey) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
    }

    /**
     * Decrypts an EncryptedAssertion from a SAML Response XML.
     * Extracts the encrypted key, unwraps it using RSA-OAEP, and decrypts the assertion.
     *
     * @param samlResponseXml the SAML Response XML containing an EncryptedAssertion
     * @return the decrypted assertion XML, or empty if decryption fails
     * @since 0.1.0
     */
    public Optional<String> decrypt(String samlResponseXml) {
        if (samlResponseXml == null || samlResponseXml.isBlank()) {
            return Optional.empty();
        }

        try {
            // Extract encrypted key
            String encryptedKeyBase64 = extractEncryptedKey(samlResponseXml);
            if (encryptedKeyBase64 == null) {
                LOG.debug("No EncryptedKey found in SAML response");
                return Optional.empty();
            }

            // Extract key encryption algorithm
            String keyAlgorithm = extractKeyAlgorithm(samlResponseXml);

            // Unwrap the AES key
            byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedKeyBase64.replaceAll("\\s+", ""));
            byte[] aesKeyBytes = unwrapKey(encryptedKeyBytes, keyAlgorithm);

            // Extract data encryption algorithm
            String dataAlgorithm = extractDataAlgorithm(samlResponseXml);
            int aesKeyLength = aesKeyBytes.length * 8; // 128 or 256

            // Extract encrypted data
            String encryptedDataBase64 = extractEncryptedData(samlResponseXml);
            if (encryptedDataBase64 == null) {
                LOG.debug("No encrypted data CipherValue found");
                return Optional.empty();
            }

            byte[] encryptedData = Base64.getDecoder().decode(encryptedDataBase64.replaceAll("\\s+", ""));

            // Decrypt
            String decrypted = decryptData(aesKeyBytes, encryptedData, dataAlgorithm);
            LOG.debug("Successfully decrypted SAML assertion ({} bytes)", decrypted.length());
            return Optional.of(decrypted);

        } catch (Exception e) {
            LOG.error("Failed to decrypt SAML EncryptedAssertion", e);
            return Optional.empty();
        }
    }

    /**
     * Unwraps an AES key using the configured RSA private key.
     *
     * @param encryptedKey the encrypted AES key bytes
     * @param algorithm    the key encryption algorithm URI
     * @return the unwrapped AES key bytes
     * @throws Exception if unwrapping fails
     * @since 0.1.0
     */
    byte[] unwrapKey(byte[] encryptedKey, String algorithm) throws Exception {
        String cipherAlg;
        if (algorithm != null && algorithm.contains("rsa-oaep")) {
            cipherAlg = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
        } else {
            cipherAlg = "RSA/ECB/PKCS1Padding";
        }
        Cipher cipher = Cipher.getInstance(cipherAlg);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedKey);
    }

    /**
     * Decrypts data using AES.
     *
     * @param aesKey        the AES key bytes
     * @param encryptedData the encrypted data (IV prepended)
     * @param algorithm     the data encryption algorithm URI
     * @return the decrypted string
     * @throws Exception if decryption fails
     * @since 0.1.0
     */
    static String decryptData(byte[] aesKey, byte[] encryptedData, String algorithm) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");

        if (algorithm != null && algorithm.contains("aes128-gcm") || algorithm != null && algorithm.contains("aes256-gcm")) {
            // AES-GCM mode
            int ivLen = 12;
            byte[] iv = new byte[ivLen];
            System.arraycopy(encryptedData, 0, iv, 0, ivLen);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] decrypted = cipher.doFinal(encryptedData, ivLen, encryptedData.length - ivLen);
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            // AES-CBC mode (default for SAML)
            int ivLen = 16;
            byte[] iv = new byte[ivLen];
            System.arraycopy(encryptedData, 0, iv, 0, ivLen);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encryptedData, ivLen, encryptedData.length - ivLen);
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    // ---- XML extraction helpers ----

    static String extractEncryptedKey(String xml) {
        // Look for CipherValue within EncryptedKey
        for (String prefix : new String[]{"xenc:", ""}) {
            String encKeyTag = "<" + prefix + "EncryptedKey";
            int ekStart = xml.indexOf(encKeyTag);
            if (ekStart >= 0) {
                String cvTag = "<" + prefix + "CipherValue>";
                int cvStart = xml.indexOf(cvTag, ekStart);
                if (cvStart >= 0) {
                    cvStart += cvTag.length();
                    String cvClose = "</" + prefix + "CipherValue>";
                    int cvEnd = xml.indexOf(cvClose, cvStart);
                    if (cvEnd >= 0) {
                        return xml.substring(cvStart, cvEnd).trim();
                    }
                }
                // Try without prefix inside EncryptedKey
                cvTag = "<CipherValue>";
                cvStart = xml.indexOf(cvTag, ekStart);
                if (cvStart >= 0) {
                    cvStart += cvTag.length();
                    String cvClose = "</CipherValue>";
                    int cvEnd = xml.indexOf(cvClose, cvStart);
                    if (cvEnd >= 0) {
                        return xml.substring(cvStart, cvEnd).trim();
                    }
                }
            }
        }
        return null;
    }

    static String extractEncryptedData(String xml) {
        // Find the second CipherValue (the data, not the key)
        // The first CipherValue in EncryptedKey is the encrypted AES key
        // The second CipherValue in EncryptedData is the actual encrypted data
        for (String prefix : new String[]{"xenc:", ""}) {
            String edTag = "<" + prefix + "EncryptedData";
            int edStart = xml.indexOf(edTag);
            if (edStart < 0) continue;

            // Find CipherValue that's NOT within EncryptedKey
            String ekTag = "<" + prefix + "EncryptedKey";
            String ekClose = "</" + prefix + "EncryptedKey>";
            int ekStart = xml.indexOf(ekTag, edStart);
            int ekEnd = ekStart >= 0 ? xml.indexOf(ekClose, ekStart) : -1;
            if (ekEnd >= 0) ekEnd += ekClose.length();

            // Look for CipherValue after EncryptedKey
            String cvTag = "<" + prefix + "CipherValue>";
            int searchFrom = ekEnd >= 0 ? ekEnd : edStart;
            int cvStart = xml.indexOf(cvTag, searchFrom);
            if (cvStart < 0) {
                cvTag = "<CipherValue>";
                cvStart = xml.indexOf(cvTag, searchFrom);
            }
            if (cvStart >= 0) {
                cvStart += cvTag.length();
                String cvClose = cvTag.replace("<", "</");
                int cvEnd = xml.indexOf(cvClose, cvStart);
                if (cvEnd >= 0) {
                    return xml.substring(cvStart, cvEnd).trim();
                }
            }
        }
        return null;
    }

    static String extractKeyAlgorithm(String xml) {
        for (String prefix : new String[]{"xenc:", ""}) {
            String tag = "<" + prefix + "EncryptionMethod";
            // Find it within EncryptedKey
            String ekTag = "<" + prefix + "EncryptedKey";
            int ekStart = xml.indexOf(ekTag);
            if (ekStart >= 0) {
                int emStart = xml.indexOf(tag, ekStart);
                if (emStart >= 0 && emStart < ekStart + 500) {
                    int algStart = xml.indexOf("Algorithm=\"", emStart);
                    if (algStart >= 0 && algStart < emStart + 200) {
                        algStart += "Algorithm=\"".length();
                        int algEnd = xml.indexOf('"', algStart);
                        if (algEnd >= 0) {
                            return xml.substring(algStart, algEnd);
                        }
                    }
                }
            }
        }
        return null;
    }

    static String extractDataAlgorithm(String xml) {
        for (String prefix : new String[]{"xenc:", ""}) {
            String tag = "<" + prefix + "EncryptedData";
            int edStart = xml.indexOf(tag);
            if (edStart >= 0) {
                String emTag = "<" + prefix + "EncryptionMethod";
                int emStart = xml.indexOf(emTag, edStart);
                if (emStart >= 0 && emStart < edStart + 300) {
                    int algStart = xml.indexOf("Algorithm=\"", emStart);
                    if (algStart >= 0 && algStart < emStart + 200) {
                        algStart += "Algorithm=\"".length();
                        int algEnd = xml.indexOf('"', algStart);
                        if (algEnd >= 0) {
                            return xml.substring(algStart, algEnd);
                        }
                    }
                }
            }
        }
        return null;
    }
}
