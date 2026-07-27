package ssg.legoflow.database.postgresql.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link Md5Auth}.
 */
class Md5AuthTest {

    @Test
    void testMethod() {
        assertThat(new Md5Auth().method()).isEqualTo("md5");
    }

    @Test
    void testComputeMd5Format() {
        byte[] salt = {0x01, 0x02, 0x03, 0x04};
        String hash = Md5Auth.computeMd5("password", "user", salt);
        assertThat(hash).startsWith("md5");
        assertThat(hash).hasSize(3 + 32); // "md5" + 32 hex chars
    }

    @Test
    void testComputeMd5Deterministic() {
        byte[] salt = {0x0A, 0x0B, 0x0C, 0x0D};
        String hash1 = Md5Auth.computeMd5("pass", "user", salt);
        String hash2 = Md5Auth.computeMd5("pass", "user", salt);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void testComputeMd5DifferentSalt() {
        byte[] salt1 = {0x01, 0x02, 0x03, 0x04};
        byte[] salt2 = {0x05, 0x06, 0x07, 0x08};
        String hash1 = Md5Auth.computeMd5("pass", "user", salt1);
        String hash2 = Md5Auth.computeMd5("pass", "user", salt2);
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testComputeMd5DifferentUsername() {
        byte[] salt = {0x01, 0x02, 0x03, 0x04};
        String hash1 = Md5Auth.computeMd5("pass", "user1", salt);
        String hash2 = Md5Auth.computeMd5("pass", "user2", salt);
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testValidateMd5Success() {
        var auth = new Md5Auth().addUser("alice", "secret");
        byte[] salt = {0x0A, 0x0B, 0x0C, 0x0D};
        String md5 = Md5Auth.computeMd5("secret", "alice", salt);
        assertThat(auth.validateMd5("alice", md5, salt)).isTrue();
    }

    @Test
    void testValidateMd5WrongPassword() {
        var auth = new Md5Auth().addUser("alice", "secret");
        byte[] salt = {0x0A, 0x0B, 0x0C, 0x0D};
        String md5 = Md5Auth.computeMd5("wrong", "alice", salt);
        assertThat(auth.validateMd5("alice", md5, salt)).isFalse();
    }

    @Test
    void testValidateMd5UnknownUser() {
        var auth = new Md5Auth().addUser("alice", "secret");
        byte[] salt = {0x0A, 0x0B, 0x0C, 0x0D};
        String md5 = Md5Auth.computeMd5("secret", "bob", salt);
        assertThat(auth.validateMd5("bob", md5, salt)).isFalse();
    }

    @Test
    void testGenerateSalt() {
        var auth = new Md5Auth();
        byte[] salt = auth.generateSalt();
        assertThat(salt).hasSize(4);
    }

    @Test
    void testGenerateSaltUnique() {
        var auth = new Md5Auth();
        byte[] salt1 = auth.generateSalt();
        byte[] salt2 = auth.generateSalt();
        // Extremely unlikely to be equal
        assertThat(salt1).isNotEqualTo(salt2);
    }

    @Test
    void testAuthenticateBasic() {
        var auth = new Md5Auth().addUser("user", "pass");
        assertThat(auth.authenticate("user", "pass")).isTrue();
        assertThat(auth.authenticate("user", "wrong")).isFalse();
    }
}
