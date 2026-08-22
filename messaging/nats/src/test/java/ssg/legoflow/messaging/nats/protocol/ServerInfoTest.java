package ssg.legoflow.messaging.nats.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ServerInfo}.
 */
class ServerInfoTest {

    @Test
    void testWithDefaults() {
        var info = ServerInfo.withDefaults("SRV1", "test", 4222);
        assertThat(info.serverId()).isEqualTo("SRV1");
        assertThat(info.serverName()).isEqualTo("test");
        assertThat(info.port()).isEqualTo(4222);
        assertThat(info.maxPayload()).isEqualTo(NatsProtocol.DEFAULT_MAX_PAYLOAD);
        assertThat(info.headers()).isTrue();
        assertThat(info.jetstream()).isTrue();
    }

    @Test
    void testWithClientId() {
        var info = ServerInfo.withDefaults("S", "N", 4222).withClientId(99);
        assertThat(info.clientId()).isEqualTo(99);
        assertThat(info.serverId()).isEqualTo("S");
    }

    @Test
    void testWithAuthRequired() {
        var info = ServerInfo.withDefaults("S", "N", 4222).withAuthRequired(true);
        assertThat(info.authRequired()).isTrue();
    }

    @Test
    void testToJsonContainsAllFields() {
        var info = new ServerInfo("id1", "name1", "1.0", "host1", 4222,
                1024, 1, true, false, true, false, 7);
        String json = info.toJson();

        assertThat(json).contains("\"server_id\":\"id1\"");
        assertThat(json).contains("\"server_name\":\"name1\"");
        assertThat(json).contains("\"version\":\"1.0\"");
        assertThat(json).contains("\"host\":\"host1\"");
        assertThat(json).contains("\"port\":4222");
        assertThat(json).contains("\"max_payload\":1024");
        assertThat(json).contains("\"proto\":1");
        assertThat(json).contains("\"headers\":true");
        assertThat(json).contains("\"jetstream\":false");
        assertThat(json).contains("\"auth_required\":true");
        assertThat(json).contains("\"client_id\":7");
    }

    @Test
    void testFromJson() {
        String json = "{\"server_id\":\"X\",\"server_name\":\"Y\",\"port\":5222}";
        var info = ServerInfo.fromJson(json);
        assertThat(info.serverId()).isEqualTo("X");
        assertThat(info.serverName()).isEqualTo("Y");
        assertThat(info.port()).isEqualTo(5222);
    }

    @Test
    void testRoundTrip() {
        var original = new ServerInfo("abc", "nats", "2.0", "192.168.1.1",
                4222, 2097152, 1, true, true, false, false, 123);
        var parsed = ServerInfo.fromJson(original.toJson());

        assertThat(parsed.serverId()).isEqualTo(original.serverId());
        assertThat(parsed.serverName()).isEqualTo(original.serverName());
        assertThat(parsed.version()).isEqualTo(original.version());
        assertThat(parsed.host()).isEqualTo(original.host());
        assertThat(parsed.port()).isEqualTo(original.port());
        assertThat(parsed.maxPayload()).isEqualTo(original.maxPayload());
        assertThat(parsed.proto()).isEqualTo(original.proto());
        assertThat(parsed.headers()).isEqualTo(original.headers());
        assertThat(parsed.jetstream()).isEqualTo(original.jetstream());
        assertThat(parsed.clientId()).isEqualTo(original.clientId());
    }

    @Test
    void testFromJsonWithDefaults() {
        var info = ServerInfo.fromJson("{}");
        assertThat(info.serverId()).isEmpty();
        assertThat(info.port()).isEqualTo(NatsProtocol.DEFAULT_PORT);
        assertThat(info.maxPayload()).isEqualTo(NatsProtocol.DEFAULT_MAX_PAYLOAD);
    }

    @Test
    void testExtractBoolFalse() {
        assertThat(ServerInfo.extractBool("{\"key\":false}", "key")).isFalse();
    }

    @Test
    void testExtractBoolMissing() {
        assertThat(ServerInfo.extractBool("{}", "missing")).isFalse();
    }
}
