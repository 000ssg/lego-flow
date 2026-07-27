package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link CapabilityFlags}.
 */
class CapabilityFlagsTest {

    @Test
    void testHasCapability_single() {
        assertThat(CapabilityFlags.hasCapability(
                CapabilityFlags.CLIENT_PROTOCOL_41, CapabilityFlags.CLIENT_PROTOCOL_41)).isTrue();
    }

    @Test
    void testHasCapability_missing() {
        assertThat(CapabilityFlags.hasCapability(0, CapabilityFlags.CLIENT_PROTOCOL_41)).isFalse();
    }

    @Test
    void testHasCapability_combined() {
        int caps = CapabilityFlags.CLIENT_PROTOCOL_41 | CapabilityFlags.CLIENT_SECURE_CONNECTION;
        assertThat(CapabilityFlags.hasCapability(caps, CapabilityFlags.CLIENT_PROTOCOL_41)).isTrue();
        assertThat(CapabilityFlags.hasCapability(caps, CapabilityFlags.CLIENT_SECURE_CONNECTION)).isTrue();
        assertThat(CapabilityFlags.hasCapability(caps, CapabilityFlags.CLIENT_COMPRESS)).isFalse();
    }

    @Test
    void testDefaultServerCapabilities_hasProtocol41() {
        assertThat(CapabilityFlags.hasCapability(
                CapabilityFlags.DEFAULT_SERVER_CAPABILITIES, CapabilityFlags.CLIENT_PROTOCOL_41)).isTrue();
    }

    @Test
    void testDefaultServerCapabilities_hasPluginAuth() {
        assertThat(CapabilityFlags.hasCapability(
                CapabilityFlags.DEFAULT_SERVER_CAPABILITIES, CapabilityFlags.CLIENT_PLUGIN_AUTH)).isTrue();
    }

    @Test
    void testDefaultServerCapabilities_hasDeprecateEof() {
        assertThat(CapabilityFlags.hasCapability(
                CapabilityFlags.DEFAULT_SERVER_CAPABILITIES, CapabilityFlags.CLIENT_DEPRECATE_EOF)).isTrue();
    }

    @Test
    void testDefaultClientCapabilities_hasSecureConnection() {
        assertThat(CapabilityFlags.hasCapability(
                CapabilityFlags.DEFAULT_CLIENT_CAPABILITIES, CapabilityFlags.CLIENT_SECURE_CONNECTION)).isTrue();
    }

    @Test
    void testToString_nonEmpty() {
        String str = CapabilityFlags.toString(CapabilityFlags.DEFAULT_SERVER_CAPABILITIES);
        assertThat(str).contains("PROTOCOL_41");
        assertThat(str).contains("PLUGIN_AUTH");
    }

    @Test
    void testToString_empty() {
        assertThat(CapabilityFlags.toString(0)).isEmpty();
    }

    @Test
    void testFlags_arePowersOfTwo() {
        assertThat(CapabilityFlags.CLIENT_LONG_PASSWORD).isEqualTo(1);
        assertThat(CapabilityFlags.CLIENT_FOUND_ROWS).isEqualTo(2);
        assertThat(CapabilityFlags.CLIENT_LONG_FLAG).isEqualTo(4);
        assertThat(CapabilityFlags.CLIENT_CONNECT_WITH_DB).isEqualTo(8);
        assertThat(CapabilityFlags.CLIENT_PROTOCOL_41).isEqualTo(512);
    }
}
