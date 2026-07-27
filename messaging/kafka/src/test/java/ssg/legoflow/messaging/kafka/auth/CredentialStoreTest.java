package ssg.legoflow.messaging.kafka.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link CredentialStore}.
 */
class CredentialStoreTest {

    @Test
    void testAddAndValidatePlainUser() {
        var store = new CredentialStore();
        store.addPlainUser("alice", "secret123");
        assertThat(store.validatePlain("alice", "secret123")).isTrue();
        assertThat(store.hasPlainUsers()).isTrue();
    }

    @Test
    void testValidatePlainWrongPassword() {
        var store = new CredentialStore();
        store.addPlainUser("alice", "secret123");
        assertThat(store.validatePlain("alice", "wrongpass")).isFalse();
    }

    @Test
    void testValidatePlainUnknownUser() {
        var store = new CredentialStore();
        assertThat(store.validatePlain("unknown", "pass")).isFalse();
        assertThat(store.hasPlainUsers()).isFalse();
    }

    @Test
    void testAddAndGetScramCredential() {
        var store = new CredentialStore();
        store.addScramUser("bob", "password456", 4096);
        var cred = store.getScramCredential("bob");
        assertThat(cred).isNotNull();
        assertThat(cred.salt()).hasSize(16);
        assertThat(cred.storedKey()).hasSize(32); // SHA-256 output
        assertThat(cred.serverKey()).hasSize(32);
        assertThat(cred.iterations()).isEqualTo(4096);
        assertThat(store.hasScramUsers()).isTrue();
    }

    @Test
    void testGetScramCredentialUnknownUser() {
        var store = new CredentialStore();
        assertThat(store.getScramCredential("unknown")).isNull();
    }
}
