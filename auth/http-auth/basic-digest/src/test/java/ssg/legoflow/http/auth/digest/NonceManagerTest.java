package ssg.legoflow.http.auth.digest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class NonceManagerTest {

    private NonceManager manager;

    @BeforeEach
    void setUp() {
        manager = new NonceManager(Duration.ofMinutes(5));
    }

    @Test
    void testGenerateNonce() {
        String nonce = manager.generateNonce();
        assertThat(nonce).isNotEmpty();
        assertThat(nonce).hasSize(48); // 24 bytes = 48 hex chars
    }

    @Test
    void testValidateValidNonce() {
        String nonce = manager.generateNonce();
        assertThat(manager.validateNonce(nonce, "00000001")).isTrue();
    }

    @Test
    void testValidateUnknownNonce() {
        assertThat(manager.validateNonce("unknown-nonce", "00000001")).isFalse();
    }

    @Test
    void testValidateNullNonce() {
        assertThat(manager.validateNonce(null, "00000001")).isFalse();
    }

    @Test
    void testNonceCountReplayDetection() {
        String nonce = manager.generateNonce();
        assertThat(manager.validateNonce(nonce, "00000001")).isTrue();
        assertThat(manager.validateNonce(nonce, "00000002")).isTrue();
        // Replay same count
        assertThat(manager.validateNonce(nonce, "00000002")).isFalse();
        // Lower count
        assertThat(manager.validateNonce(nonce, "00000001")).isFalse();
    }

    @Test
    void testRemoveNonce() {
        String nonce = manager.generateNonce();
        manager.removeNonce(nonce);
        assertThat(manager.validateNonce(nonce, "00000001")).isFalse();
    }

    @Test
    void testExpiredNonce() {
        var shortLived = new NonceManager(Duration.ofMillis(1));
        String nonce = shortLived.generateNonce();
        // Wait for expiry
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        assertThat(shortLived.validateNonce(nonce, "00000001")).isFalse();
        assertThat(shortLived.isStale(nonce)).isTrue();
    }

    @Test
    void testIsStaleUnknown() {
        assertThat(manager.isStale("unknown")).isTrue();
    }

    @Test
    void testIsStaleValid() {
        String nonce = manager.generateNonce();
        assertThat(manager.isStale(nonce)).isFalse();
    }

    @Test
    void testCleanExpired() {
        var shortLived = new NonceManager(Duration.ofMillis(1));
        shortLived.generateNonce();
        shortLived.generateNonce();
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        int cleaned = shortLived.cleanExpired();
        assertThat(cleaned).isEqualTo(2);
        assertThat(shortLived.size()).isEqualTo(0);
    }

    @Test
    void testSize() {
        assertThat(manager.size()).isEqualTo(0);
        manager.generateNonce();
        assertThat(manager.size()).isEqualTo(1);
        manager.generateNonce();
        assertThat(manager.size()).isEqualTo(2);
    }

    @Test
    void testDefaultConstructor() {
        var defaultManager = new NonceManager();
        String nonce = defaultManager.generateNonce();
        assertThat(defaultManager.validateNonce(nonce, null)).isTrue();
    }

    @Test
    void testValidateWithNullNc() {
        String nonce = manager.generateNonce();
        assertThat(manager.validateNonce(nonce, null)).isTrue();
    }

    @Test
    void testValidateWithInvalidNcFormat() {
        String nonce = manager.generateNonce();
        assertThat(manager.validateNonce(nonce, "not-hex")).isFalse();
    }

    @Test
    void testUniqueNonces() {
        String n1 = manager.generateNonce();
        String n2 = manager.generateNonce();
        assertThat(n1).isNotEqualTo(n2);
    }
}
