package ssg.legoflow.http.auth.oauth2.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class TokenStoreTest {

    private TokenStore store;

    @BeforeEach
    void setUp() {
        store = new TokenStore();
    }

    @Test
    void testIssueAndValidateAccessToken() {
        var token = store.issueAccessToken("client1", "alice", Set.of("read"));
        assertThat(token.token()).isNotEmpty();
        assertThat(store.validateAccessToken(token.token())).isPresent();
    }

    @Test
    void testIssueAndValidateRefreshToken() {
        var token = store.issueRefreshToken("client1", "alice", Set.of("read"));
        assertThat(store.validateRefreshToken(token.token())).isPresent();
    }

    @Test
    void testRevokeAccessToken() {
        var token = store.issueAccessToken("c", "u", Set.of());
        assertThat(store.revokeAccessToken(token.token())).isTrue();
        assertThat(store.validateAccessToken(token.token())).isEmpty();
    }

    @Test
    void testRevokeRefreshToken() {
        var token = store.issueRefreshToken("c", "u", Set.of());
        assertThat(store.revokeRefreshToken(token.token())).isTrue();
        assertThat(store.validateRefreshToken(token.token())).isEmpty();
    }

    @Test
    void testRevokeBySubject() {
        store.issueAccessToken("c", "alice", Set.of());
        store.issueRefreshToken("c", "alice", Set.of());
        store.issueAccessToken("c", "bob", Set.of());
        store.revokeBySubject("alice");
        assertThat(store.accessTokenCount()).isEqualTo(1);
    }

    @Test
    void testValidateInvalidToken() {
        assertThat(store.validateAccessToken("nonexistent")).isEmpty();
        assertThat(store.validateRefreshToken("nonexistent")).isEmpty();
    }

    @Test
    void testExpiredAccessToken() {
        var shortLived = new TokenStore(Duration.ofMillis(1), Duration.ofDays(30));
        var token = shortLived.issueAccessToken("c", "u", Set.of());
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        assertThat(shortLived.validateAccessToken(token.token())).isEmpty();
    }

    @Test
    void testTokenCounts() {
        store.issueAccessToken("c", "u", Set.of());
        store.issueAccessToken("c", "u", Set.of());
        store.issueRefreshToken("c", "u", Set.of());
        assertThat(store.accessTokenCount()).isEqualTo(2);
        assertThat(store.refreshTokenCount()).isEqualTo(1);
    }

    @Test
    void testAccessTokenLifetime() {
        assertThat(store.getAccessTokenLifetime()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void testStoredTokenMetadata() {
        var token = store.issueAccessToken("client1", "alice", Set.of("read", "write"));
        assertThat(token.clientId()).isEqualTo("client1");
        assertThat(token.subject()).isEqualTo("alice");
        assertThat(token.scopes()).containsExactlyInAnyOrder("read", "write");
        assertThat(token.issuedAt()).isNotNull();
        assertThat(token.expiresAt()).isNotNull();
        assertThat(token.isExpired()).isFalse();
    }
}
