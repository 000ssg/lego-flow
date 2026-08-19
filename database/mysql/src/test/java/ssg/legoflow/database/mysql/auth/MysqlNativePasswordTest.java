package ssg.legoflow.database.mysql.auth;

import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link MysqlNativePassword}.
 */
class MysqlNativePasswordTest {

    private final MysqlNativePassword plugin = MysqlNativePassword.INSTANCE;

    @Test
    void testName() {
        assertThat(plugin.name()).isEqualTo("mysql_native_password");
    }

    @Test
    void testGenerateAuthResponse_nonEmpty() {
        var scramble = new byte[20];
        new SecureRandom().nextBytes(scramble);

        var response = plugin.generateAuthResponse("password", scramble);
        assertThat(response).hasSize(20); // SHA-1 output
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

        var storedHash = MysqlNativePassword.computeStoredHash("secret");
        var authResponse = plugin.generateAuthResponse("secret", scramble);

        assertThat(plugin.verify(authResponse, scramble, storedHash)).isTrue();
    }

    @Test
    void testVerify_wrongPassword() {
        var scramble = new byte[20];
        new SecureRandom().nextBytes(scramble);

        var storedHash = MysqlNativePassword.computeStoredHash("secret");
        var authResponse = plugin.generateAuthResponse("wrong", scramble);

        assertThat(plugin.verify(authResponse, scramble, storedHash)).isFalse();
    }

    @Test
    void testVerify_emptyPassword_bothEmpty() {
        assertThat(plugin.verify(new byte[0], new byte[20], new byte[0])).isTrue();
    }

    @Test
    void testVerify_emptyResponse_nonEmptyHash() {
        var storedHash = MysqlNativePassword.computeStoredHash("secret");
        assertThat(plugin.verify(new byte[0], new byte[20], storedHash)).isFalse();
    }

    @Test
    void testComputeStoredHash_nonEmpty() {
        var hash = MysqlNativePassword.computeStoredHash("password");
        assertThat(hash).hasSize(20); // SHA-1 digest
    }

    @Test
    void testComputeStoredHash_empty() {
        assertThat(MysqlNativePassword.computeStoredHash("")).isEmpty();
    }

    @Test
    void testComputeStoredHash_null() {
        assertThat(MysqlNativePassword.computeStoredHash(null)).isEmpty();
    }

    @Test
    void testComputeStoredHash_deterministic() {
        var hash1 = MysqlNativePassword.computeStoredHash("test");
        var hash2 = MysqlNativePassword.computeStoredHash("test");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void testDifferentScramble_differentResponse() {
        var scramble1 = new byte[20];
        var scramble2 = new byte[20];
        new SecureRandom().nextBytes(scramble1);
        new SecureRandom().nextBytes(scramble2);

        var response1 = plugin.generateAuthResponse("pass", scramble1);
        var response2 = plugin.generateAuthResponse("pass", scramble2);

        assertThat(response1).isNotEqualTo(response2);
    }

    @Test
    void testVerify_withDifferentScrambles() {
        var scramble = new byte[20];
        new SecureRandom().nextBytes(scramble);

        var storedHash = MysqlNativePassword.computeStoredHash("mypass");
        var authResponse = plugin.generateAuthResponse("mypass", scramble);

        // Verify with correct scramble
        assertThat(plugin.verify(authResponse, scramble, storedHash)).isTrue();

        // Verify with wrong scramble
        var wrongScramble = new byte[20];
        new SecureRandom().nextBytes(wrongScramble);
        assertThat(plugin.verify(authResponse, wrongScramble, storedHash)).isFalse();
    }
}
