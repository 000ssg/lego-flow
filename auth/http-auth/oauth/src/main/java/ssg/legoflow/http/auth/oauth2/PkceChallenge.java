package ssg.legoflow.http.auth.oauth2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE (Proof Key for Code Exchange, RFC 7636) code_verifier and code_challenge generation.
 * Supports S256 (recommended) and plain challenge methods.
 *
 * @since 0.1.0
 */
public class PkceChallenge {

    /** S256 challenge method — SHA-256 hash of the verifier. */
    public static final String METHOD_S256 = "S256";
    /** Plain challenge method — verifier is used directly (not recommended). */
    public static final String METHOD_PLAIN = "plain";

    private static final String UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String codeVerifier;
    private final String codeChallenge;
    private final String challengeMethod;

    private PkceChallenge(String codeVerifier, String codeChallenge, String challengeMethod) {
        this.codeVerifier = codeVerifier;
        this.codeChallenge = codeChallenge;
        this.challengeMethod = challengeMethod;
    }

    /**
     * Generates a PKCE challenge using S256 method.
     *
     * @return the PKCE challenge
     * @since 0.1.0
     */
    public static PkceChallenge generateS256() {
        String verifier = generateCodeVerifier(43);
        String challenge = computeS256Challenge(verifier);
        return new PkceChallenge(verifier, challenge, METHOD_S256);
    }

    /**
     * Generates a PKCE challenge using S256 method with custom verifier length.
     *
     * @param verifierLength the length of the code verifier (43-128)
     * @return the PKCE challenge
     * @since 0.1.0
     */
    public static PkceChallenge generateS256(int verifierLength) {
        if (verifierLength < 43 || verifierLength > 128) {
            throw new IllegalArgumentException("Verifier length must be 43-128, got: " + verifierLength);
        }
        String verifier = generateCodeVerifier(verifierLength);
        String challenge = computeS256Challenge(verifier);
        return new PkceChallenge(verifier, challenge, METHOD_S256);
    }

    /**
     * Generates a PKCE challenge using plain method.
     *
     * @return the PKCE challenge
     * @since 0.1.0
     */
    public static PkceChallenge generatePlain() {
        String verifier = generateCodeVerifier(43);
        return new PkceChallenge(verifier, verifier, METHOD_PLAIN);
    }

    /**
     * Verifies a code_verifier against a code_challenge.
     *
     * @param verifier        the code verifier
     * @param challenge       the code challenge
     * @param challengeMethod the challenge method (S256 or plain)
     * @return true if the verifier matches the challenge
     * @since 0.1.0
     */
    public static boolean verify(String verifier, String challenge, String challengeMethod) {
        if (verifier == null || challenge == null) return false;
        return switch (challengeMethod) {
            case METHOD_S256 -> computeS256Challenge(verifier).equals(challenge);
            case METHOD_PLAIN -> verifier.equals(challenge);
            default -> false;
        };
    }

    /**
     * Computes S256 challenge: BASE64URL(SHA256(code_verifier)).
     *
     * @param verifier the code verifier
     * @return the S256 challenge
     * @since 0.1.0
     */
    public static String computeS256Challenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Generates a random code verifier.
     *
     * @param length the length (43-128)
     * @return the code verifier
     * @since 0.1.0
     */
    public static String generateCodeVerifier(int length) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(UNRESERVED.charAt(RANDOM.nextInt(UNRESERVED.length())));
        }
        return sb.toString();
    }

    // Getters

    /** Returns the code verifier. */
    public String getCodeVerifier() { return codeVerifier; }
    /** Returns the code challenge. */
    public String getCodeChallenge() { return codeChallenge; }
    /** Returns the challenge method. */
    public String getChallengeMethod() { return challengeMethod; }
}
