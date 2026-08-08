package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecurityLevelTest {

    @Test void testNoAuthNoPrivFlags() {
        assertThat(SecurityLevel.NO_AUTH_NO_PRIV.flags()).isEqualTo(0x00);
    }

    @Test void testAuthNoPrivFlags() {
        assertThat(SecurityLevel.AUTH_NO_PRIV.flags()).isEqualTo(0x01);
    }

    @Test void testAuthPrivFlags() {
        assertThat(SecurityLevel.AUTH_PRIV.flags()).isEqualTo(0x03);
    }

    @Test void testNoAuthNoPrivNotAuthenticated() {
        assertThat(SecurityLevel.NO_AUTH_NO_PRIV.isAuthenticated()).isFalse();
    }

    @Test void testNoAuthNoPrivNotPrivate() {
        assertThat(SecurityLevel.NO_AUTH_NO_PRIV.isPrivate()).isFalse();
    }

    @Test void testAuthNoPrivIsAuthenticated() {
        assertThat(SecurityLevel.AUTH_NO_PRIV.isAuthenticated()).isTrue();
    }

    @Test void testAuthNoPrivNotPrivate() {
        assertThat(SecurityLevel.AUTH_NO_PRIV.isPrivate()).isFalse();
    }

    @Test void testAuthPrivIsAuthenticated() {
        assertThat(SecurityLevel.AUTH_PRIV.isAuthenticated()).isTrue();
    }

    @Test void testAuthPrivIsPrivate() {
        assertThat(SecurityLevel.AUTH_PRIV.isPrivate()).isTrue();
    }

    @Test void testFromFlagsZeroReturnsNoAuthNoPriv() {
        assertThat(SecurityLevel.fromFlags(0x00)).isEqualTo(SecurityLevel.NO_AUTH_NO_PRIV);
    }

    @Test void testFromFlagsOneReturnsAuthNoPriv() {
        assertThat(SecurityLevel.fromFlags(0x01)).isEqualTo(SecurityLevel.AUTH_NO_PRIV);
    }

    @Test void testFromFlagsThreeReturnsAuthPriv() {
        assertThat(SecurityLevel.fromFlags(0x03)).isEqualTo(SecurityLevel.AUTH_PRIV);
    }

    @Test void testFromFlagsMaskExtraBits() {
        // Flags beyond lower 2 bits should be masked away
        assertThat(SecurityLevel.fromFlags(0xFF)).isEqualTo(SecurityLevel.AUTH_PRIV); // 0xFF & 0x03 = 0x03
        assertThat(SecurityLevel.fromFlags(0x81)).isEqualTo(SecurityLevel.AUTH_NO_PRIV); // 0x81 & 0x03 = 0x01
    }

    @Test void testFromFlagsTwoThrowsPrivacyWithoutAuth() {
        // 0x02 = privacy without authentication (invalid)
        assertThatThrownBy(() -> SecurityLevel.fromFlags(0x02))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid security level flags");
    }

    @Test void testFromFlagsNegativeIsMasked() {
        // -1 in binary is all 1s, -1 & 0x03 = 0x03 → AUTH_PRIV
        assertThat(SecurityLevel.fromFlags(-1)).isEqualTo(SecurityLevel.AUTH_PRIV);
    }

    @Test void testAllValuesIterate() {
        var values = SecurityLevel.values();
        assertThat(values).hasSize(3);
        // Each value should have a unique flags value
        for (var level : values) {
            var fromFlags = SecurityLevel.fromFlags(level.flags());
            assertThat(fromFlags).isEqualTo(level);
        }
    }

    @Test void testAuthPrivIsMostRestrictive() {
        var authPriv = SecurityLevel.AUTH_PRIV;
        // Both authenticated and private = most restrictive
        assertThat(authPriv.isAuthenticated()).isTrue();
        assertThat(authPriv.isPrivate()).isTrue();
    }

    @Test void testNoAuthNoPrivIsLeastRestrictive() {
        var noAuth = SecurityLevel.NO_AUTH_NO_PRIV;
        // Neither authenticated nor private = least restrictive
        assertThat(noAuth.isAuthenticated()).isFalse();
        assertThat(noAuth.isPrivate()).isFalse();
    }
}
