package ssg.legoflow.http.auth.oidc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
/**
 * JSON Web Key Set (JWK Set) per RFC 7517. Parses the {@code {"keys":[...]}} format
 * and extracts RSA public keys for JWT signature verification.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>RSA keys (kty=RSA) with modulus (n) and exponent (e)</li>
 *   <li>Key ID (kid) based lookup for key rotation</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class JwkSet {

    private static final Logger LOG = LoggerFactory.getLogger(JwkSet.class);

    private final Map<String, PublicKey> keys = new LinkedHashMap<>();
    private final List<PublicKey> allKeys = new ArrayList<>();

    /**
     * Parses a JWK Set from a JSON string.
     *
     * @param json the JWK Set JSON (e.g., from a jwks_uri endpoint)
     * @return the parsed JWK Set
     * @since 0.1.0
     */
    public static JwkSet fromJson(String json) {
        var jwkSet = new JwkSet();
        if (json == null || json.isBlank()) return jwkSet;

        // Find the "keys" array
        int keysStart = json.indexOf("\"keys\"");
        if (keysStart < 0) return jwkSet;

        int arrayStart = json.indexOf('[', keysStart);
        if (arrayStart < 0) return jwkSet;

        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayEnd < 0) return jwkSet;

        String keysArray = json.substring(arrayStart + 1, arrayEnd);

        // Parse individual key objects
        int pos = 0;
        while (pos < keysArray.length()) {
            int objStart = keysArray.indexOf('{', pos);
            if (objStart < 0) break;
            int objEnd = findMatchingBrace(keysArray, objStart);
            if (objEnd < 0) break;

            String keyObj = keysArray.substring(objStart, objEnd + 1);
            jwkSet.parseKey(keyObj);
            pos = objEnd + 1;
        }

        LOG.debug("Parsed JWK Set with {} keys", jwkSet.allKeys.size());
        return jwkSet;
    }

    /**
     * Looks up a public key by key ID (kid).
     *
     * @param kid the key ID
     * @return the public key, or empty if not found
     * @since 0.1.0
     */
    public Optional<PublicKey> getKey(String kid) {
        return Optional.ofNullable(keys.get(kid));
    }

    /**
     * Returns the first available public key. Useful when kid is not specified.
     *
     * @return the first key, or empty if no keys
     * @since 0.1.0
     */
    public Optional<PublicKey> getFirstKey() {
        return allKeys.isEmpty() ? Optional.empty() : Optional.of(allKeys.getFirst());
    }

    /**
     * Returns all public keys in this set.
     *
     * @return unmodifiable list of public keys
     * @since 0.1.0
     */
    public List<PublicKey> getAllKeys() {
        return Collections.unmodifiableList(allKeys);
    }

    /**
     * Returns the number of keys in this set.
     *
     * @return the key count
     * @since 0.1.0
     */
    public int size() {
        return allKeys.size();
    }

    /**
     * Returns all key IDs in this set.
     *
     * @return unmodifiable set of key IDs
     * @since 0.1.0
     */
    public Set<String> getKeyIds() {
        return Collections.unmodifiableSet(keys.keySet());
    }

    private void parseKey(String keyJson) {
        String kty = extractJsonStringValue(keyJson, "kty");
        if (!"RSA".equals(kty)) {
            LOG.debug("Skipping non-RSA key type: {}", kty);
            return;
        }

        String n = extractJsonStringValue(keyJson, "n");
        String e = extractJsonStringValue(keyJson, "e");
        String kid = extractJsonStringValue(keyJson, "kid");

        if (n == null || e == null) {
            LOG.warn("RSA key missing n or e parameter");
            return;
        }

        try {
            BigInteger modulus = base64UrlToBigInteger(n);
            BigInteger exponent = base64UrlToBigInteger(e);
            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);

            allKeys.add(publicKey);
            if (kid != null) {
                keys.put(kid, publicKey);
            }
            LOG.debug("Parsed RSA public key with kid={}", kid);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            LOG.error("Failed to parse RSA public key", ex);
        }
    }

    private static BigInteger base64UrlToBigInteger(String base64url) {
        byte[] bytes = Base64.getUrlDecoder().decode(base64url);
        return new BigInteger(1, bytes);
    }

    static String extractJsonStringValue(String json, String key) {
        String search = "\"" + key + "\"";
        int keyStart = json.indexOf(search);
        if (keyStart < 0) return null;
        int colonPos = json.indexOf(':', keyStart + search.length());
        if (colonPos < 0) return null;

        // Skip whitespace after colon
        int valStart = colonPos + 1;
        while (valStart < json.length() && Character.isWhitespace(json.charAt(valStart))) valStart++;

        if (valStart >= json.length()) return null;

        if (json.charAt(valStart) == '"') {
            int valEnd = json.indexOf('"', valStart + 1);
            if (valEnd < 0) return null;
            return json.substring(valStart + 1, valEnd);
        }

        // Unquoted value (number, boolean, null)
        int valEnd = valStart;
        while (valEnd < json.length() && json.charAt(valEnd) != ',' && json.charAt(valEnd) != '}'
               && json.charAt(valEnd) != ']' && !Character.isWhitespace(json.charAt(valEnd))) {
            valEnd++;
        }
        return json.substring(valStart, valEnd);
    }

    private static int findMatchingBracket(String json, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static int findMatchingBrace(String json, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }
}
