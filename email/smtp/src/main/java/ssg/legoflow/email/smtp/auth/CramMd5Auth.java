package ssg.legoflow.email.smtp.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * CRAM-MD5 SASL authentication mechanism per RFC 2195.
 *
 * <p>CRAM-MD5 is a challenge-response mechanism that avoids sending the password
 * in cleartext. The exchange:
 * <ol>
 *   <li>Server sends 334 with Base64-encoded challenge (timestamp)</li>
 *   <li>Client computes HMAC-MD5(password, challenge)</li>
 *   <li>Client sends Base64("username HMAC-digest")</li>
 * </ol>
 *
 * <p>While more secure than PLAIN/LOGIN over unencrypted connections, CRAM-MD5
 * has weaknesses and TLS is still recommended.
 *
 * @since 0.1.0
 */
public final class CramMd5Auth implements SmtpAuthenticator {

    private static final String HMAC_MD5 = "HmacMD5";

    private final String username;
    private final String password;
    private boolean complete = false;

    /**
     * Creates a CRAM-MD5 authenticator.
     *
     * @param username the username
     * @param password the password (used as HMAC key)
     */
    public CramMd5Auth(String username, String password) {
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
    }

    @Override
    public String mechanism() {
        return "CRAM-MD5";
    }

    @Override
    public String initialResponse() {
        // CRAM-MD5 waits for a challenge
        return null;
    }

    @Override
    public String respond(String challenge) throws SmtpAuthException {
        if (complete) {
            throw new SmtpAuthException("CRAM-MD5 mechanism does not expect additional challenges");
        }

        String decodedChallenge;
        try {
            decodedChallenge = new String(Base64.getDecoder().decode(challenge), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new SmtpAuthException("Invalid Base64 challenge", e);
        }

        String digest = computeHmacMd5(password, decodedChallenge);
        String response = username + " " + digest;
        complete = true;
        return Base64.getEncoder().encodeToString(response.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    /**
     * Computes the HMAC-MD5 digest.
     *
     * @param key  the HMAC key (password)
     * @param data the data to hash (challenge)
     * @return the hex-encoded digest
     * @throws SmtpAuthException if HMAC computation fails
     */
    public static String computeHmacMd5(String key, String data) throws SmtpAuthException {
        try {
            Mac mac = Mac.getInstance(HMAC_MD5);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_MD5));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new SmtpAuthException("HMAC-MD5 computation failed", e);
        }
    }

    /**
     * Verifies a CRAM-MD5 response against expected credentials.
     *
     * @param base64Response the Base64-encoded response ("username digest")
     * @param challenge      the original challenge (not Base64-encoded)
     * @param expectedUser   the expected username
     * @param expectedPass   the expected password
     * @return true if the response is valid
     * @throws SmtpAuthException if verification computation fails
     */
    public static boolean verify(String base64Response, String challenge,
                                 String expectedUser, String expectedPass)
            throws SmtpAuthException {
        String decoded = new String(Base64.getDecoder().decode(base64Response), StandardCharsets.UTF_8);
        int spaceIdx = decoded.indexOf(' ');
        if (spaceIdx < 0) {
            return false;
        }
        String user = decoded.substring(0, spaceIdx);
        String digest = decoded.substring(spaceIdx + 1);

        if (!user.equals(expectedUser)) {
            return false;
        }

        String expected = computeHmacMd5(expectedPass, challenge);
        return expected.equals(digest);
    }

    /**
     * Generates a CRAM-MD5 challenge string.
     *
     * <p>Format: {@code <process-id.timestamp@hostname>}
     *
     * @param hostname the server hostname
     * @return the Base64-encoded challenge
     */
    public static String generateChallenge(String hostname) {
        String challenge = "<" + ProcessHandle.current().pid() + "."
                + System.currentTimeMillis() + "@" + hostname + ">";
        return Base64.getEncoder().encodeToString(challenge.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
