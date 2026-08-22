package ssg.legoflow.http.auth.basic;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class InMemoryUserStoreTest {

    @Test
    void testAddAndAuthenticate() {
        var store = new InMemoryUserStore().addUser("alice", "pass", Set.of("admin"));
        var principal = store.authenticate("alice", "pass");
        assertThat(principal).isPresent();
        assertThat(principal.get().getName()).isEqualTo("alice");
        assertThat(principal.get().getRoles()).contains("admin");
    }

    @Test
    void testAuthenticateWrongPassword() {
        var store = new InMemoryUserStore().addUser("alice", "pass");
        assertThat(store.authenticate("alice", "wrong")).isEmpty();
    }

    @Test
    void testAuthenticateUnknownUser() {
        var store = new InMemoryUserStore();
        assertThat(store.authenticate("unknown", "pass")).isEmpty();
    }

    @Test
    void testUserExists() {
        var store = new InMemoryUserStore().addUser("alice", "pass");
        assertThat(store.userExists("alice")).isTrue();
        assertThat(store.userExists("bob")).isFalse();
    }

    @Test
    void testRemoveUser() {
        var store = new InMemoryUserStore().addUser("alice", "pass");
        store.removeUser("alice");
        assertThat(store.userExists("alice")).isFalse();
    }

    @Test
    void testFindByUsername() {
        var store = new InMemoryUserStore().addUser("alice", "pass", Set.of("admin"));
        assertThat(store.findByUsername("alice")).isPresent();
        assertThat(store.findByUsername("unknown")).isEmpty();
    }

    @Test
    void testGetPassword() {
        var store = new InMemoryUserStore().addUser("alice", "secret");
        assertThat(store.getPassword("alice")).contains("secret");
        assertThat(store.getPassword("unknown")).isEmpty();
    }

    @Test
    void testSize() {
        var store = new InMemoryUserStore();
        assertThat(store.size()).isEqualTo(0);
        store.addUser("a", "p").addUser("b", "p");
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void testChaining() {
        var store = new InMemoryUserStore()
                .addUser("a", "1")
                .addUser("b", "2")
                .addUser("c", "3");
        assertThat(store.size()).isEqualTo(3);
    }
}
