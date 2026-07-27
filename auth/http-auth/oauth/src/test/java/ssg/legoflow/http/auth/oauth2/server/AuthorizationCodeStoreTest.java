package ssg.legoflow.http.auth.oauth2.server;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class AuthorizationCodeStoreTest {

    @Test
    void testGenerateAndConsume() {
        var store = new AuthorizationCodeStore();
        var code = store.generate("client1", "http://localhost/cb", Set.of("read"), "alice", null, null);
        assertThat(code.code()).isNotEmpty();
        assertThat(store.size()).isEqualTo(1);
        var consumed = store.consume(code.code());
        assertThat(consumed).isPresent();
        assertThat(consumed.get().subject()).isEqualTo("alice");
        assertThat(store.size()).isEqualTo(0);
    }

    @Test
    void testConsumeOnlyOnce() {
        var store = new AuthorizationCodeStore();
        var code = store.generate("c", "http://localhost", Set.of(), "u", null, null);
        assertThat(store.consume(code.code())).isPresent();
        assertThat(store.consume(code.code())).isEmpty();
    }

    @Test
    void testConsumeInvalidCode() {
        var store = new AuthorizationCodeStore();
        assertThat(store.consume("nonexistent")).isEmpty();
    }

    @Test
    void testExpiredCode() {
        var store = new AuthorizationCodeStore(Duration.ofMillis(1));
        var code = store.generate("c", "http://localhost", Set.of(), "u", null, null);
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        assertThat(store.consume(code.code())).isEmpty();
    }

    @Test
    void testWithPkce() {
        var store = new AuthorizationCodeStore();
        var code = store.generate("c", "http://localhost", Set.of(), "u", "challenge", "S256");
        var consumed = store.consume(code.code());
        assertThat(consumed).isPresent();
        assertThat(consumed.get().codeChallenge()).isEqualTo("challenge");
        assertThat(consumed.get().challengeMethod()).isEqualTo("S256");
    }

    @Test
    void testCleanExpired() {
        var store = new AuthorizationCodeStore(Duration.ofMillis(1));
        store.generate("c", "u", Set.of(), "u", null, null);
        store.generate("c", "u", Set.of(), "u", null, null);
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        int cleaned = store.cleanExpired();
        assertThat(cleaned).isEqualTo(2);
    }

    @Test
    void testCodeMetadata() {
        var store = new AuthorizationCodeStore();
        var code = store.generate("client1", "http://localhost/cb", Set.of("read", "write"), "alice", null, null);
        assertThat(code.clientId()).isEqualTo("client1");
        assertThat(code.redirectUri()).isEqualTo("http://localhost/cb");
        assertThat(code.scopes()).containsExactlyInAnyOrder("read", "write");
        assertThat(code.createdAt()).isNotNull();
    }
}
