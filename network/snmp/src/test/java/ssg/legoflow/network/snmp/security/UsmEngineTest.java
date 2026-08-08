package ssg.legoflow.network.snmp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link UsmEngine} authentication and encryption.
 *
 * @since 0.1.0
 */
class UsmEngineTest {

    private byte[] engineId;
    private UsmEngine engine;

    @BeforeEach
    void setUp() {
        engineId = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        engine = new UsmEngine(engineId, 1);
    }

    @Test
    void testEngineIdIsCopied() {
        byte[] id = engine.engineId();
        id[0] = (byte) 0xFF;
        assertThat(engine.engineId()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void testEngineBoots() {
        assertThat(engine.engineBoots()).isEqualTo(1);
    }

    @Test
    void testAddAndGetUser() {
        UsmUser user = UsmUser.noAuth("testUser");
        engine.addUser(user);

        assertThat(engine.getUser("testUser")).isNotNull();
        assertThat(engine.getUser("testUser").userName()).isEqualTo("testUser");
    }

    @Test
    void testRemoveUser() {
        engine.addUser(UsmUser.noAuth("tempUser"));
        engine.removeUser("tempUser");
        assertThat(engine.getUser("tempUser")).isNull();
    }

    @Test
    void testGetNonexistentUserReturnsNull() {
        assertThat(engine.getUser("ghost")).isNull();
    }

    // ── Authentication ──

    @Test
    void testComputeAuthMd5() {
        byte[] authKey = UsmKeyUtils.deriveLocalizedKey("password", engineId,
                AuthProtocol.HMAC_MD5_96);
        UsmUser user = UsmUser.authNoPriv("admin", AuthProtocol.HMAC_MD5_96, authKey);

        byte[] message = "Test SNMP message data for HMAC computation".getBytes();
        byte[] digest = engine.computeAuth(message, user);

        assertThat(digest).hasSize(12); // Truncated to 12 bytes
    }

    @Test
    void testComputeAuthSha1() {
        byte[] authKey = UsmKeyUtils.deriveLocalizedKey("password", engineId,
                AuthProtocol.HMAC_SHA_96);
        UsmUser user = UsmUser.authNoPriv("admin", AuthProtocol.HMAC_SHA_96, authKey);

        byte[] message = "Test SNMP message data".getBytes();
        byte[] digest = engine.computeAuth(message, user);

        assertThat(digest).hasSize(12);
    }

    @Test
    void testAuthVerification() {
        byte[] authKey = UsmKeyUtils.deriveLocalizedKey("secret", engineId,
                AuthProtocol.HMAC_MD5_96);
        UsmUser user = UsmUser.authNoPriv("admin", AuthProtocol.HMAC_MD5_96, authKey);

        byte[] message = "Important message content".getBytes();
        byte[] digest = engine.computeAuth(message, user);

        assertThat(engine.verifyAuth(message, digest, user)).isTrue();
    }

    @Test
    void testAuthVerificationFailsOnTamperedMessage() {
        byte[] authKey = UsmKeyUtils.deriveLocalizedKey("secret", engineId,
                AuthProtocol.HMAC_MD5_96);
        UsmUser user = UsmUser.authNoPriv("admin", AuthProtocol.HMAC_MD5_96, authKey);

        byte[] message = "Original message".getBytes();
        byte[] digest = engine.computeAuth(message, user);

        byte[] tampered = "Tampered message".getBytes();
        assertThat(engine.verifyAuth(tampered, digest, user)).isFalse();
    }

    @Test
    void testAuthNoAuthReturnsEmpty() {
        UsmUser user = UsmUser.noAuth("public");
        byte[] digest = engine.computeAuth("data".getBytes(), user);
        assertThat(digest).isEmpty();
    }

    // ── DES Encryption ──

    @Test
    void testDesEncryptDecryptRoundTrip() {
        byte[] authKey = UsmKeyUtils.deriveLocalizedKey("authpass", engineId,
                AuthProtocol.HMAC_MD5_96);
        byte[] privKey = UsmKeyUtils.derivePrivLocalizedKey("privpass", engineId,
                AuthProtocol.HMAC_MD5_96, PrivProtocol.DES_CBC);

        UsmUser user = UsmUser.authPriv("admin",
                AuthProtocol.HMAC_MD5_96, authKey,
                PrivProtocol.DES_CBC, privKey);

        byte[] plaintext = "This is a test scoped PDU that needs encryption".getBytes();

        UsmEngine.EncryptionResult result = engine.encrypt(plaintext, user, 1, 100);
        assertThat(result.encryptedData()).isNotEqualTo(plaintext);
        assertThat(result.privParams()).hasSize(8);

        byte[] decrypted = engine.decrypt(result.encryptedData(), user,
                result.privParams(), 1, 100);
        // DES pads to 8-byte boundary, so decrypted may be longer
        byte[] trimmed = new byte[plaintext.length];
        System.arraycopy(decrypted, 0, trimmed, 0, plaintext.length);
        assertThat(trimmed).isEqualTo(plaintext);
    }

    // ── AES Encryption ──

    @Test
    void testAesEncryptDecryptRoundTrip() {
        byte[] authKey = UsmKeyUtils.deriveLocalizedKey("authpass", engineId,
                AuthProtocol.HMAC_SHA_96);
        byte[] privKey = UsmKeyUtils.derivePrivLocalizedKey("privpass", engineId,
                AuthProtocol.HMAC_SHA_96, PrivProtocol.AES_128_CFB);

        UsmUser user = UsmUser.authPriv("admin",
                AuthProtocol.HMAC_SHA_96, authKey,
                PrivProtocol.AES_128_CFB, privKey);

        byte[] plaintext = "AES encrypted scoped PDU data payload".getBytes();

        UsmEngine.EncryptionResult result = engine.encrypt(plaintext, user, 2, 200);
        assertThat(result.encryptedData()).isNotEqualTo(plaintext);
        assertThat(result.privParams()).hasSize(8);

        byte[] decrypted = engine.decrypt(result.encryptedData(), user,
                result.privParams(), 2, 200);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void testNoPrivEncryptReturnsOriginal() {
        UsmUser user = UsmUser.noAuth("public");
        byte[] data = "plaintext".getBytes();

        UsmEngine.EncryptionResult result = engine.encrypt(data, user, 0, 0);
        assertThat(result.encryptedData()).isEqualTo(data);
        assertThat(result.privParams()).isEmpty();
    }

    @Test
    void testEngineIdRejectsEmpty() {
        assertThatThrownBy(() -> new UsmEngine(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEngineIdRejectsNull() {
        assertThatThrownBy(() -> new UsmEngine(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
