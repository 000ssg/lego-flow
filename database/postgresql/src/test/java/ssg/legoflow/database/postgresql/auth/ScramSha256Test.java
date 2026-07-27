package ssg.legoflow.database.postgresql.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ScramSha256} and {@link ScramUtils}.
 */
class ScramSha256Test {

    @Test
    void testMethod() {
        assertThat(new ScramSha256().method()).isEqualTo("scram-sha-256");
    }

    @Test
    void testMechanism() {
        assertThat(ScramSha256.MECHANISM).isEqualTo("SCRAM-SHA-256");
    }

    @Test
    void testAuthenticateSuccess() {
        var auth = new ScramSha256().addUser("alice", "secret");
        assertThat(auth.authenticate("alice", "secret")).isTrue();
    }

    @Test
    void testAuthenticateWrongPassword() {
        var auth = new ScramSha256().addUser("alice", "secret");
        assertThat(auth.authenticate("alice", "wrong")).isFalse();
    }

    @Test
    void testAuthenticateUnknownUser() {
        var auth = new ScramSha256().addUser("alice", "secret");
        assertThat(auth.authenticate("bob", "secret")).isFalse();
    }

    @Test
    void testFullHandshake() {
        // Setup
        var auth = new ScramSha256().addUser("alice", "secret");
        var cred = auth.getCredentials("alice");
        assertThat(cred).isNotNull();

        // Client creates first message
        var clientSession = new ScramSha256.ClientSession("alice", "secret", "testNonce123");
        String clientFirst = clientSession.createClientFirstMessage();
        assertThat(clientFirst).startsWith("n,,n=alice,r=testNonce123");

        // Server processes client first
        var serverSession = new ScramSha256.ServerSession(cred);
        String serverFirst = serverSession.processClientFirst(clientFirst);
        assertThat(serverFirst).contains("r=testNonce123");
        assertThat(serverFirst).contains("s=");
        assertThat(serverFirst).contains("i=");

        // Client processes server first
        String clientFinal = clientSession.processServerFirst(serverFirst);
        assertThat(clientFinal).contains("c=biws"); // base64("n,,")
        assertThat(clientFinal).contains("p=");

        // Server processes client final
        String serverFinal = serverSession.processClientFinal(clientFinal);
        assertThat(serverFinal).isNotNull();
        assertThat(serverFinal).startsWith("v=");

        // Client verifies server final
        assertThat(clientSession.verifyServerFinal(serverFinal)).isTrue();
    }

    @Test
    void testHandshakeWithWrongPassword() {
        var auth = new ScramSha256().addUser("alice", "secret");
        var cred = auth.getCredentials("alice");

        var clientSession = new ScramSha256.ClientSession("alice", "wrong_password", "nonce1");
        String clientFirst = clientSession.createClientFirstMessage();

        var serverSession = new ScramSha256.ServerSession(cred);
        String serverFirst = serverSession.processClientFirst(clientFirst);
        String clientFinal = clientSession.processServerFirst(serverFirst);

        // Server should reject
        String serverFinal = serverSession.processClientFinal(clientFinal);
        assertThat(serverFinal).isNull();
    }

    @Test
    void testHandshakeWithCustomIterations() {
        var auth = new ScramSha256().withIterations(2048).addUser("bob", "password");
        var cred = auth.getCredentials("bob");
        assertThat(cred.iterations()).isEqualTo(2048);

        var clientSession = new ScramSha256.ClientSession("bob", "password", "nonce2");
        String clientFirst = clientSession.createClientFirstMessage();

        var serverSession = new ScramSha256.ServerSession(cred);
        String serverFirst = serverSession.processClientFirst(clientFirst);
        String clientFinal = clientSession.processServerFirst(serverFirst);

        String serverFinal = serverSession.processClientFinal(clientFinal);
        assertThat(serverFinal).isNotNull();
        assertThat(clientSession.verifyServerFinal(serverFinal)).isTrue();
    }

    @Test
    void testStoredCredentials() {
        var auth = new ScramSha256().addUser("alice", "secret");
        var cred = auth.getCredentials("alice");

        assertThat(cred).isNotNull();
        assertThat(cred.salt()).hasSize(16);
        assertThat(cred.storedKey()).hasSize(32); // SHA-256 output
        assertThat(cred.serverKey()).hasSize(32);
        assertThat(cred.iterations()).isEqualTo(ScramSha256.DEFAULT_ITERATIONS);
    }

    @Test
    void testCredentialsNotFound() {
        var auth = new ScramSha256();
        assertThat(auth.getCredentials("nobody")).isNull();
    }

    @Test
    void testNonceStartsWithClientNonce() {
        var auth = new ScramSha256().addUser("user", "pass");
        var cred = auth.getCredentials("user");

        var clientSession = new ScramSha256.ClientSession("user", "pass", "myClientNonce");
        String clientFirst = clientSession.createClientFirstMessage();

        var serverSession = new ScramSha256.ServerSession(cred);
        String serverFirst = serverSession.processClientFirst(clientFirst);

        // Server nonce should start with client nonce
        var attrs = ScramSha256.parseAttributes(serverFirst);
        assertThat(attrs.get("r")).startsWith("myClientNonce");
    }

    @Test
    void testClientFirstMessageFormat() {
        var session = new ScramSha256.ClientSession("testuser", "pass", "abc123");
        String msg = session.createClientFirstMessage();
        assertThat(msg).isEqualTo("n,,n=testuser,r=abc123");
    }

    @Test
    void testServerNonceRejection() {
        var auth = new ScramSha256().addUser("user", "pass");
        var cred = auth.getCredentials("user");

        var clientSession = new ScramSha256.ClientSession("user", "pass", "myNonce");
        clientSession.createClientFirstMessage();

        // Forge a server first message with wrong nonce
        String fakeServerFirst = "r=differentNonce,s=" + ScramUtils.base64Encode(cred.salt()) + ",i=4096";
        assertThatThrownBy(() -> clientSession.processServerFirst(fakeServerFirst))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nonce");
    }
}
