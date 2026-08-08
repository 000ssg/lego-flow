package ssg.legoflow.wamp.core.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * WAMP Challenge-Response Authentication (WAMP-CRA).
 * Uses HMAC-SHA256 to sign a challenge string provided by the router.
 *
 * <p>Flow:
 * <ol>
 *   <li>Client sends HELLO with {@code authid} and {@code authmethods: ["wampcra"]}</li>
 *   <li>Router sends CHALLENGE with a JSON challenge string</li>
 *   <li>Client computes HMAC-SHA256(secret, challenge) and sends AUTHENTICATE</li>
 *   <li>Router verifies the signature and sends WELCOME or ABORT</li>
 * </ol>
 *
 * @since 0.1.0
 */
public class WampCraAuth {

    /** The authentication method identifier for WAMP-CRA. */
    public static final String AUTH_METHOD = "wampcra";

    /**
     * Generates a challenge string for the given session and auth ID.
     *
     * @param sessionId the session ID
     * @param authId    the authentication identity
     * @param authRole  the authentication role
     * @return the challenge details map containing the challenge string
     */
    public static Map<String, Object> generateChallenge(long sessionId, String authId, String authRole) {
        String challenge = "{\"nonce\":\"" + System.nanoTime() + "\","
                + "\"authprovider\":\"static\","
                + "\"authid\":\"" + authId + "\","
                + "\"authrole\":\"" + authRole + "\","
                + "\"authmethod\":\"wampcra\","
                + "\"session\":" + sessionId + "}";
        return Map.of("challenge", challenge);
    }

    /**
     * Signs a challenge string with the given secret using HMAC-SHA256.
     *
     * @param challenge the challenge string to sign
     * @param secret    the shared secret
     * @return the Base64-encoded signature
     */
    public static String sign(String challenge, String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(challenge.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("WAMP-CRA signing failed", e);
        }
    }

    /**
     * Verifies that a signature matches the expected HMAC-SHA256 of the challenge.
     *
     * @param challenge the original challenge string
     * @param secret    the shared secret
     * @param signature the Base64-encoded signature to verify
     * @return {@code true} if the signature is valid
     */
    public static boolean verify(String challenge, String secret, String signature) {
        String expected = sign(challenge, secret);
        return expected.equals(signature);
    }
}
