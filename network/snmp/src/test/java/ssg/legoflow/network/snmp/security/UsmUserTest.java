package ssg.legoflow.network.snmp.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link UsmUser}.
 *
 * @since 0.1.0
 */
class UsmUserTest {

    @Test
    void testNoAuth() {
        var user = UsmUser.noAuth("admin");
        assertThat(user.userName()).isEqualTo("admin");
        assertThat(user.authProtocol()).isEqualTo(AuthProtocol.NONE);
        assertThat(user.privProtocol()).isEqualTo(PrivProtocol.NONE);
        assertThat(user.authKey()).isEmpty();
        assertThat(user.privKey()).isEmpty();
    }

    @Test
    void testAuthNoPriv() {
        var authKey = new byte[]{1, 2, 3, 4};
        var user = UsmUser.authNoPriv("user1", AuthProtocol.HMAC_MD5_96, authKey);
        assertThat(user.userName()).isEqualTo("user1");
        assertThat(user.authProtocol()).isEqualTo(AuthProtocol.HMAC_MD5_96);
        assertThat(user.authKey()).containsExactly(1, 2, 3, 4);
        assertThat(user.privProtocol()).isEqualTo(PrivProtocol.NONE);
    }

    @Test
    void testAuthPriv() {
        var authKey = new byte[]{1, 2, 3, 4};
        var privKey = new byte[]{5, 6, 7, 8};
        var user = UsmUser.authPriv("user2", AuthProtocol.HMAC_SHA_96, authKey,
                PrivProtocol.AES_128_CFB, privKey);
        assertThat(user.userName()).isEqualTo("user2");
        assertThat(user.authProtocol()).isEqualTo(AuthProtocol.HMAC_SHA_96);
        assertThat(user.privProtocol()).isEqualTo(PrivProtocol.AES_128_CFB);
        assertThat(user.authKey()).containsExactly(1, 2, 3, 4);
        assertThat(user.privKey()).containsExactly(5, 6, 7, 8);
    }

    @Test
    void testConstructorNullUserNameThrows() {
        assertThatThrownBy(() -> new UsmUser(null, AuthProtocol.NONE, new byte[0], PrivProtocol.NONE, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructorEmptyUserNameThrows() {
        assertThatThrownBy(() -> new UsmUser("", AuthProtocol.NONE, new byte[0], PrivProtocol.NONE, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructorNullAuthProtocolThrows() {
        assertThatThrownBy(() -> new UsmUser("user", null, new byte[0], PrivProtocol.NONE, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructorNullPrivProtocolThrows() {
        assertThatThrownBy(() -> new UsmUser("user", AuthProtocol.NONE, new byte[0], null, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDefensiveCopyAuthKey() {
        var authKey = new byte[]{1, 2};
        var user = UsmUser.noAuth("admin");
        // authKey is already empty but let's verify via constructor
        user = new UsmUser("test", AuthProtocol.NONE, authKey, PrivProtocol.NONE, null);
        authKey[0] = 99;
        assertThat(user.authKey()[0]).isEqualTo((byte) 1);
    }

    @Test
    void testNullKeysBecomeEmpty() {
        var user = new UsmUser("test", AuthProtocol.NONE, null, PrivProtocol.NONE, null);
        assertThat(user.authKey()).isEmpty();
        assertThat(user.privKey()).isEmpty();
    }

    @Test
    void testEqualsAndHashCode() {
        var u1 = UsmUser.noAuth("admin");
        var u2 = UsmUser.noAuth("admin");
        var u3 = UsmUser.noAuth("other");
        assertThat(u1).isEqualTo(u2);
        assertThat(u1.hashCode()).isEqualTo(u2.hashCode());
        assertThat(u1).isNotEqualTo(u3);
    }

    @Test
    void testToString() {
        var user = UsmUser.noAuth("admin");
        assertThat(user.toString()).contains("admin");
        assertThat(user.toString()).contains("auth=NONE");
    }
}
