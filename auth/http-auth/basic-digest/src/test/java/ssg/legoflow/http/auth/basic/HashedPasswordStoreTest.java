package ssg.legoflow.http.auth.basic;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class HashedPasswordStoreTest {

    @Test
    void testAddAndAuthenticate() {
        var store = new HashedPasswordStore().addUser("alice", "password123", Set.of("admin"));
        var principal = store.authenticate("alice", "password123");
        assertThat(principal).isPresent();
        assertThat(principal.get().getName()).isEqualTo("alice");
    }

    @Test
    void testWrongPassword() {
        var store = new HashedPasswordStore().addUser("alice", "password123");
        assertThat(store.authenticate("alice", "wrong")).isEmpty();
    }

    @Test
    void testUnknownUser() {
        var store = new HashedPasswordStore();
        assertThat(store.authenticate("unknown", "pass")).isEmpty();
    }

    @Test
    void testUserExists() {
        var store = new HashedPasswordStore().addUser("alice", "pass");
        assertThat(store.userExists("alice")).isTrue();
        assertThat(store.userExists("unknown")).isFalse();
    }

    @Test
    void testFindByUsername() {
        var store = new HashedPasswordStore().addUser("alice", "pass");
        assertThat(store.findByUsername("alice")).isPresent();
        assertThat(store.findByUsername("unknown")).isEmpty();
    }

    @Test
    void testGetPasswordReturnsEmpty() {
        var store = new HashedPasswordStore().addUser("alice", "pass");
        // Hashed store cannot return plaintext
        assertThat(store.getPassword("alice")).isEmpty();
    }

    @Test
    void testHashPassword() {
        byte[] salt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        byte[] hash = HashedPasswordStore.hashPassword("test", salt);
        assertThat(hash).isNotEmpty();
        // Same input should produce same hash
        byte[] hash2 = HashedPasswordStore.hashPassword("test", salt);
        assertThat(hash).isEqualTo(hash2);
    }

    @Test
    void testBytesToHexAndBack() {
        byte[] original = {0x0A, 0x1B, 0x2C, (byte) 0xFF};
        String hex = HashedPasswordStore.bytesToHex(original);
        assertThat(hex).isEqualTo("0a1b2cff");
        byte[] back = HashedPasswordStore.hexToBytes(hex);
        assertThat(back).isEqualTo(original);
    }

    @Test
    void testSize() {
        var store = new HashedPasswordStore().addUser("a", "1").addUser("b", "2");
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void testDifferentSaltsProduceDifferentHashes() {
        var store1 = new HashedPasswordStore();
        var store2 = new HashedPasswordStore();
        store1.addUser("alice", "pass");
        store2.addUser("alice", "pass");
        // Both should authenticate successfully despite different salts
        assertThat(store1.authenticate("alice", "pass")).isPresent();
        assertThat(store2.authenticate("alice", "pass")).isPresent();
    }
}
