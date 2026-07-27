package ssg.legoflow.messaging.kafka.auth;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ScramSha256Server}.
 */
class ScramSha256ServerTest {

    @Test
    void testFullScramExchange() throws Exception {
        var store = new CredentialStore();
        store.addScramUser("bob", "pencil", 4096);
        var server = new ScramSha256Server(store);

        // Step 1: Client sends client-first-message
        String clientNonce = "rOprNGfwEbeRWgbNEkqO";
        String clientFirstMsg = "n,,n=bob,r=" + clientNonce;
        byte[] serverFirstBytes = server.evaluateResponse(clientFirstMsg.getBytes(StandardCharsets.UTF_8));
        assertThat(server.isComplete()).isFalse();

        // Parse server-first-message
        String serverFirstMsg = new String(serverFirstBytes, StandardCharsets.UTF_8);
        assertThat(serverFirstMsg).startsWith("r=" + clientNonce); // combined nonce starts with client nonce
        assertThat(serverFirstMsg).contains(",s=");
        assertThat(serverFirstMsg).contains(",i=4096");

        // Extract server params
        String[] parts = serverFirstMsg.split(",");
        String combinedNonce = parts[0].substring(2);
        String saltBase64 = parts[1].substring(2);
        int iterations = Integer.parseInt(parts[2].substring(2));

        // Step 2: Client computes proof
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        byte[] saltedPassword = hi("pencil", salt, iterations);
        byte[] clientKey = hmacSha256(saltedPassword, "Client Key".getBytes());
        byte[] storedKey = sha256(clientKey);

        String clientFirstMsgBare = "n=bob,r=" + clientNonce;
        String clientFinalWithoutProof = "c=biws,r=" + combinedNonce;
        String authMessage = clientFirstMsgBare + "," + serverFirstMsg + "," + clientFinalWithoutProof;

        byte[] clientSignature = hmacSha256(storedKey, authMessage.getBytes(StandardCharsets.UTF_8));
        byte[] clientProof = xor(clientKey, clientSignature);

        String clientFinalMsg = clientFinalWithoutProof + ",p=" + Base64.getEncoder().encodeToString(clientProof);

        // Step 3: Send client-final-message
        byte[] serverFinalBytes = server.evaluateResponse(clientFinalMsg.getBytes(StandardCharsets.UTF_8));
        assertThat(server.isComplete()).isTrue();
        assertThat(server.authenticatedUser()).isEqualTo("bob");

        // Verify server-final-message contains server signature
        String serverFinalMsg = new String(serverFinalBytes, StandardCharsets.UTF_8);
        assertThat(serverFinalMsg).startsWith("v=");

        // Verify the server signature
        byte[] serverKey = hmacSha256(saltedPassword, "Server Key".getBytes());
        byte[] expectedServerSig = hmacSha256(serverKey, authMessage.getBytes(StandardCharsets.UTF_8));
        String expectedServerSigBase64 = Base64.getEncoder().encodeToString(expectedServerSig);
        assertThat(serverFinalMsg).isEqualTo("v=" + expectedServerSigBase64);
    }

    @Test
    void testWrongPassword() throws Exception {
        var store = new CredentialStore();
        store.addScramUser("bob", "pencil", 4096);
        var server = new ScramSha256Server(store);

        // Step 1: Client-first
        String clientNonce = "testNonce123";
        String clientFirstMsg = "n,,n=bob,r=" + clientNonce;
        byte[] serverFirstBytes = server.evaluateResponse(clientFirstMsg.getBytes(StandardCharsets.UTF_8));
        String serverFirstMsg = new String(serverFirstBytes, StandardCharsets.UTF_8);

        // Parse server response
        String[] parts = serverFirstMsg.split(",");
        String combinedNonce = parts[0].substring(2);
        String saltBase64 = parts[1].substring(2);
        int iterations = Integer.parseInt(parts[2].substring(2));

        // Compute with WRONG password
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        byte[] saltedPassword = hi("wrongpassword", salt, iterations);
        byte[] clientKey = hmacSha256(saltedPassword, "Client Key".getBytes());
        byte[] storedKey = sha256(clientKey);

        String clientFirstMsgBare = "n=bob,r=" + clientNonce;
        String clientFinalWithoutProof = "c=biws,r=" + combinedNonce;
        String authMessage = clientFirstMsgBare + "," + serverFirstMsg + "," + clientFinalWithoutProof;

        byte[] clientSignature = hmacSha256(storedKey, authMessage.getBytes(StandardCharsets.UTF_8));
        byte[] clientProof = xor(clientKey, clientSignature);

        String clientFinalMsg = clientFinalWithoutProof + ",p=" + Base64.getEncoder().encodeToString(clientProof);

        // Step 2: Should fail
        assertThatThrownBy(() -> server.evaluateResponse(clientFinalMsg.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Authentication failed");
        assertThat(server.isComplete()).isFalse();
    }

    @Test
    void testUnknownUser() {
        var store = new CredentialStore();
        var server = new ScramSha256Server(store);

        String clientFirstMsg = "n,,n=unknown,r=nonce123";
        assertThatThrownBy(() -> server.evaluateResponse(clientFirstMsg.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Unknown user");
    }

    @Test
    void testInvalidClientFirstMessage() {
        var store = new CredentialStore();
        store.addScramUser("bob", "pencil", 4096);
        var server = new ScramSha256Server(store);

        // Missing GS2 header
        String badMsg = "n=bob,r=nonce";
        assertThatThrownBy(() -> server.evaluateResponse(badMsg.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("GS2 header");
    }

    @Test
    void testNonceMismatch() throws Exception {
        var store = new CredentialStore();
        store.addScramUser("bob", "pencil", 4096);
        var server = new ScramSha256Server(store);

        // Step 1
        String clientFirstMsg = "n,,n=bob,r=clientNonce123";
        server.evaluateResponse(clientFirstMsg.getBytes(StandardCharsets.UTF_8));

        // Step 2: Send wrong nonce
        String badFinalMsg = "c=biws,r=wrongNonce,p=AAAA";
        assertThatThrownBy(() -> server.evaluateResponse(badFinalMsg.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Nonce mismatch");
    }

    @Test
    void testMechanismName() {
        var server = new ScramSha256Server(new CredentialStore());
        assertThat(server.mechanismName()).isEqualTo("SCRAM-SHA-256");
    }

    // --- Crypto helpers (client side) ---

    private static byte[] hi(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }
}
