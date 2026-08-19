package ssg.legoflow.network.snmp.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link UsmKeyUtils} key derivation and localization.
 *
 * @since 0.1.0
 */
class UsmKeyUtilsTest {

    @Test
    void testPasswordToKeyMd5ProducesCorrectLength() {
        byte[] key = UsmKeyUtils.passwordToKey("maplesyrup", "MD5");
        assertThat(key).hasSize(16); // MD5 produces 16 bytes
    }

    @Test
    void testPasswordToKeySha1ProducesCorrectLength() {
        byte[] key = UsmKeyUtils.passwordToKey("maplesyrup", "SHA-1");
        assertThat(key).hasSize(20); // SHA-1 produces 20 bytes
    }

    @Test
    void testPasswordToKeyIsDeterministic() {
        byte[] key1 = UsmKeyUtils.passwordToKey("testpassword", "MD5");
        byte[] key2 = UsmKeyUtils.passwordToKey("testpassword", "MD5");
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void testDifferentPasswordsProduceDifferentKeys() {
        byte[] key1 = UsmKeyUtils.passwordToKey("password1", "MD5");
        byte[] key2 = UsmKeyUtils.passwordToKey("password2", "MD5");
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void testLocalizeKeyMd5() {
        byte[] masterKey = UsmKeyUtils.passwordToKey("maplesyrup", "MD5");
        byte[] engineId = new byte[]{0x01, 0x02, 0x03, 0x04};
        byte[] localizedKey = UsmKeyUtils.localizeKey(masterKey, engineId, "MD5");

        assertThat(localizedKey).hasSize(16);
        assertThat(localizedKey).isNotEqualTo(masterKey);
    }

    @Test
    void testLocalizeKeySha1() {
        byte[] masterKey = UsmKeyUtils.passwordToKey("maplesyrup", "SHA-1");
        byte[] engineId = new byte[]{0x01, 0x02, 0x03, 0x04};
        byte[] localizedKey = UsmKeyUtils.localizeKey(masterKey, engineId, "SHA-1");

        assertThat(localizedKey).hasSize(20);
    }

    @Test
    void testLocalizeKeyWithDifferentEngineIds() {
        byte[] masterKey = UsmKeyUtils.passwordToKey("testpw", "MD5");
        byte[] key1 = UsmKeyUtils.localizeKey(masterKey, new byte[]{0x01}, "MD5");
        byte[] key2 = UsmKeyUtils.localizeKey(masterKey, new byte[]{0x02}, "MD5");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void testDeriveLocalizedKey() {
        byte[] engineId = new byte[]{0x0A, 0x0B, 0x0C};
        byte[] key = UsmKeyUtils.deriveLocalizedKey("secret", engineId, AuthProtocol.HMAC_MD5_96);
        assertThat(key).hasSize(16);
    }

    @Test
    void testDeriveLocalizedKeyNoneReturnsEmpty() {
        byte[] key = UsmKeyUtils.deriveLocalizedKey("secret", new byte[]{1}, AuthProtocol.NONE);
        assertThat(key).isEmpty();
    }

    @Test
    void testDerivePrivLocalizedKey() {
        byte[] engineId = new byte[]{0x01, 0x02};
        byte[] key = UsmKeyUtils.derivePrivLocalizedKey("privpass", engineId,
                AuthProtocol.HMAC_SHA_96, PrivProtocol.AES_128_CFB);
        assertThat(key).hasSize(16);
    }

    @Test
    void testDerivePrivLocalizedKeyDesFromMd5() {
        byte[] engineId = new byte[]{0x01, 0x02};
        byte[] key = UsmKeyUtils.derivePrivLocalizedKey("privpass", engineId,
                AuthProtocol.HMAC_MD5_96, PrivProtocol.DES_CBC);
        assertThat(key).hasSize(16);
    }

    @Test
    void testDerivePrivLocalizedKeyNoneReturnsEmpty() {
        byte[] key = UsmKeyUtils.derivePrivLocalizedKey("secret", new byte[]{1},
                AuthProtocol.HMAC_MD5_96, PrivProtocol.NONE);
        assertThat(key).isEmpty();
    }

    @Test
    void testPasswordToKeyRejectsNull() {
        assertThatThrownBy(() -> UsmKeyUtils.passwordToKey(null, "MD5"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPasswordToKeyRejectsEmpty() {
        assertThatThrownBy(() -> UsmKeyUtils.passwordToKey("", "MD5"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
