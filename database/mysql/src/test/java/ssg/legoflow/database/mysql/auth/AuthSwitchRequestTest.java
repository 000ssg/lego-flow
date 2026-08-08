package ssg.legoflow.database.mysql.auth;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;

class AuthSwitchRequestTest {

    @Test void testHeaderConstants() {
        assertThat(AuthSwitchRequest.HEADER).isEqualTo(0xFE);
        assertThat(AuthSwitchRequest.MORE_DATA_HEADER).isEqualTo(0x01);
    }

    @Test void testIsAuthSwitchTrue() {
        byte[] payload = new byte[]{(byte)0xFE, 'a', 'b', 0};
        assertThat(AuthSwitchRequest.isAuthSwitch(payload)).isTrue();
    }

    @Test void testIsAuthSwitchFalseWrongHeader() {
        byte[] payload = new byte[]{(byte)0xFD, 'a'};
        assertThat(AuthSwitchRequest.isAuthSwitch(payload)).isFalse();
    }

    @Test void testIsAuthSwitchFalseTooShort() {
        byte[] payload = new byte[]{(byte)0xFE};
        assertThat(AuthSwitchRequest.isAuthSwitch(payload)).isFalse();
    }

    @Test void testIsAuthMoreDataTrue() {
        byte[] payload = new byte[]{0x01, 'a', 'b'};
        assertThat(AuthSwitchRequest.isAuthMoreData(payload)).isTrue();
    }

    @Test void testIsAuthMoreDataFalseWrongHeader() {
        byte[] payload = new byte[]{0x02, 'a'};
        assertThat(AuthSwitchRequest.isAuthMoreData(payload)).isFalse();
    }

    @Test void testIsAuthMoreDataFalseTooShort() {
        byte[] payload = new byte[]{0x01};
        assertThat(AuthSwitchRequest.isAuthMoreData(payload)).isFalse();
    }

    @Test void testEncodeDecodeRoundTrip() {
        byte[] data = "scramble-challenge".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var original = new AuthSwitchRequest("caching_sha2_password", data);

        byte[] encoded = original.encode();
        assertThat(encoded[0]).isEqualTo((byte)AuthSwitchRequest.HEADER);

        var decoded = AuthSwitchRequest.decode(encoded);
        assertThat(decoded.pluginName()).isEqualTo("caching_sha2_password");
        assertThat(decoded.pluginData()).isEqualTo(data);
    }

    @Test void testEncodeIncludesHeader() {
        var req = new AuthSwitchRequest("mysql_native_password", "data".getBytes());
        byte[] encoded = req.encode();

        assertThat(encoded[0]).isEqualTo((byte)0xFE); // Header
    }

    @Test void testEncodeEmptyPluginData() {
        var req = new AuthSwitchRequest("some_plugin", new byte[0]);
        byte[] encoded = req.encode();

        var decoded = AuthSwitchRequest.decode(encoded);
        assertThat(decoded.pluginName()).isEqualTo("some_plugin");
        assertThat(decoded.pluginData()).isEmpty();
    }

    @Test void testDecodeTrailingNullRemoved() {
        // Build: header(0xFE) + null-terminated plugin name + data + trailing null
        String pluginName = "test_plugin";
        byte[] pluginData = new byte[]{1, 2, 3};
        
        var buf = ByteBuffer.allocate(1 + pluginName.length() + 1 + pluginData.length + 1);
        buf.put((byte) AuthSwitchRequest.HEADER);
        for (byte b : pluginName.getBytes()) buf.put(b);
        buf.put((byte) 0); // null terminator for name
        buf.put(pluginData);
        buf.put((byte) 0); // trailing null
        
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);

        var decoded = AuthSwitchRequest.decode(payload);
        assertThat(decoded.pluginName()).isEqualTo("test_plugin");
        assertThat(decoded.pluginData()).containsExactly(1, 2, 3); // no trailing null
    }

    @Test void testDecodeNoTrailingNull() {
        // Build without trailing null
        String pluginName = "plugin";
        byte[] pluginData = new byte[]{42};
        
        var buf = ByteBuffer.allocate(1 + pluginName.length() + 1 + pluginData.length);
        buf.put((byte) AuthSwitchRequest.HEADER);
        for (byte b : pluginName.getBytes()) buf.put(b);
        buf.put((byte) 0); // null terminator
        buf.put(pluginData); // no trailing null
        
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);

        var decoded = AuthSwitchRequest.decode(payload);
        assertThat(decoded.pluginName()).isEqualTo("plugin");
        assertThat(decoded.pluginData()).containsExactly((byte)42);
    }

    @Test void testEncodeDecodeWithLongPluginName() {
        String longName = "very_long_auth_plugin_name_for_testing";
        byte[] data = new byte[64];
        for (int i = 0; i < 64; i++) data[i] = (byte)i;
        
        var original = new AuthSwitchRequest(longName, data);
        var decoded = AuthSwitchRequest.decode(original.encode());
        
        assertThat(decoded.pluginName()).isEqualTo(longName);
        assertThat(decoded.pluginData()).isEqualTo(data);
    }

    @Test void testEncodeDecodeRoundTripNativePassword() {
        byte[] scramble = new byte[20]; // MySQL native password uses 20-byte scramble
        for (int i = 0; i < 20; i++) scramble[i] = (byte)(i + 1);
        
        var req = new AuthSwitchRequest("mysql_native_password", scramble);
        var decoded = AuthSwitchRequest.decode(req.encode());
        
        assertThat(decoded.pluginName()).isEqualTo("mysql_native_password");
        assertThat(decoded.pluginData()).hasSize(20);
    }
}
