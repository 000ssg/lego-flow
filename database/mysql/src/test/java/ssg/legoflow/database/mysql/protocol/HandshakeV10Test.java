package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link HandshakeV10}.
 */
class HandshakeV10Test {

    @Test
    void testCreate_defaultValues() {
        var hs = HandshakeV10.create(1);

        assertThat(hs.protocolVersion()).isEqualTo(10);
        assertThat(hs.serverVersion()).isEqualTo(HandshakeV10.DEFAULT_SERVER_VERSION);
        assertThat(hs.connectionId()).isEqualTo(1);
        assertThat(hs.authPluginDataPart1()).hasSize(8);
        assertThat(hs.authPluginDataPart2()).hasSize(12);
        assertThat(hs.authPluginName()).isEqualTo("mysql_native_password");
        assertThat(hs.characterSet()).isEqualTo(45);
    }

    @Test
    void testCreate_customAuthPlugin() {
        var hs = HandshakeV10.create(5, "caching_sha2_password");
        assertThat(hs.authPluginName()).isEqualTo("caching_sha2_password");
        assertThat(hs.connectionId()).isEqualTo(5);
    }

    @Test
    void testEncodeDecode_roundTrip() {
        var original = HandshakeV10.create(42);
        var encoded = original.encode();
        var decoded = HandshakeV10.decode(encoded);

        assertThat(decoded.protocolVersion()).isEqualTo(original.protocolVersion());
        assertThat(decoded.serverVersion()).isEqualTo(original.serverVersion());
        assertThat(decoded.connectionId()).isEqualTo(42);
        assertThat(decoded.authPluginDataPart1()).isEqualTo(original.authPluginDataPart1());
        assertThat(decoded.authPluginName()).isEqualTo(original.authPluginName());
        assertThat(decoded.characterSet()).isEqualTo(original.characterSet());
    }

    @Test
    void testAuthPluginData_combined() {
        var hs = HandshakeV10.create(1);
        var combined = hs.authPluginData();
        assertThat(combined).hasSize(20); // 8 + 12
        // First 8 bytes should match part1
        var part1 = hs.authPluginDataPart1();
        for (int i = 0; i < 8; i++) {
            assertThat(combined[i]).isEqualTo(part1[i]);
        }
    }

    @Test
    void testCapabilityFlags_combined() {
        var hs = HandshakeV10.create(1);
        int caps = hs.capabilityFlags();
        assertThat(CapabilityFlags.hasCapability(caps, CapabilityFlags.CLIENT_PROTOCOL_41)).isTrue();
        assertThat(CapabilityFlags.hasCapability(caps, CapabilityFlags.CLIENT_SECURE_CONNECTION)).isTrue();
        assertThat(CapabilityFlags.hasCapability(caps, CapabilityFlags.CLIENT_PLUGIN_AUTH)).isTrue();
    }

    @Test
    void testStatusFlags_default() {
        var hs = HandshakeV10.create(1);
        assertThat(StatusFlags.hasStatus(hs.statusFlags(), StatusFlags.SERVER_STATUS_AUTOCOMMIT)).isTrue();
    }

    @Test
    void testAuthPluginDataPart1_randomness() {
        var hs1 = HandshakeV10.create(1);
        var hs2 = HandshakeV10.create(2);
        // Very unlikely to be equal
        assertThat(hs1.authPluginDataPart1()).isNotEqualTo(hs2.authPluginDataPart1());
    }

    @Test
    void testProtocolVersion_alwaysTen() {
        assertThat(HandshakeV10.PROTOCOL_VERSION).isEqualTo(10);
    }

    @Test
    void testEncode_startsWithProtocolVersion() {
        var hs = HandshakeV10.create(1);
        var encoded = hs.encode();
        assertThat(encoded[0]).isEqualTo((byte) 10);
    }
}
