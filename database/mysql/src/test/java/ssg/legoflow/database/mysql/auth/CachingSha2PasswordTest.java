package ssg.legoflow.database.mysql.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link CachingSha2Password}.
 */
class CachingSha2PasswordTest {

    private final CachingSha2Password plugin = CachingSha2Password.INSTANCE;

    @Test
    void testName() {
        assertThat(plugin.name()).isEqualTo("caching_sha2_password");
    }

    @Test
    void testGenerateAuthResponse_nonEmpty() {
        var scramble = new byte[20];
        new SecureRandom().nextBytes(scramble);

        var response = plugin.generateAuthResponse("password", scramble);
        assertThat(response).hasSize(32); // SHA-256 output
    }

    @Test
    void testGenerateAuthResponse_emptyPassword() {
        var response = plugin.generateAuthResponse("", new byte[20]);
        assertThat(response).isEmpty();
    }

    @Test
    void testGenerateAuthResponse_nullPassword() {
        var response = plugin.generateAuthResponse(null, new byte[20]);
        assertThat(response).isEmpty();
    }

    @Test
    void testVerify_correctPassword() {
        var scramble = new byte[20];
        new SecureRandom().nextBytes(scramble);

        var storedHash = CachingSha2Password.computeStoredHash("secret");
        var authResponse = plugin.generateAuthResponse("secret", scramble);

        assertThat(plugin.verify(authResponse, scramble, storedHash)).isTrue();
    }

    @Test
    void testVerify_wrongPassword() {
        var scramble = new byte[20];
        new SecureRandom().nextBytes(scramble);

        var storedHash = CachingSha2Password.computeStoredHash("secret");
        var authResponse = plugin.generateAuthResponse("wrong", scramble);

        assertThat(plugin.verify(authResponse, scramble, storedHash)).isFalse();
    }

    @Test
    void testVerify_emptyBothEmpty() {
        assertThat(plugin.verify(new byte[0], new byte[20], new byte[0])).isTrue();
    }

    @Test
    void testComputeStoredHash_nonEmpty() {
        var hash = CachingSha2Password.computeStoredHash("password");
        assertThat(hash).hasSize(32); // SHA-256
    }

    @Test
    void testComputeStoredHash_empty() {
        assertThat(CachingSha2Password.computeStoredHash("")).isEmpty();
    }

    @Test
    void testComputeStoredHash_deterministic() {
        var hash1 = CachingSha2Password.computeStoredHash("test");
        var hash2 = CachingSha2Password.computeStoredHash("test");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void testDifferentPassword_differentHash() {
        var hash1 = CachingSha2Password.computeStoredHash("pass1");
        var hash2 = CachingSha2Password.computeStoredHash("pass2");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testConstants() {
        assertThat(CachingSha2Password.FAST_AUTH_SUCCESS).isEqualTo((byte) 0x03);
        assertThat(CachingSha2Password.FULL_AUTH_REQUIRED).isEqualTo((byte) 0x04);
    }
}
