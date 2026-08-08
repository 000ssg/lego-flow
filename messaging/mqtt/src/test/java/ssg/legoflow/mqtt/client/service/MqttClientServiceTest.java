package ssg.legoflow.mqtt.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for MQTT client service DP/DF compliance. */
class MqttClientServiceTest {

    @Test void testBuilderCreatesService() {
        var service = MqttClientService.builder("localhost", 1883).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = MqttClientService.builder("localhost", 1883).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = MqttClientService.builder("localhost", 1883).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = MqttClientService.builder("mqtt.local", 1883).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = MqttClientService.builder("localhost", 1883).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetClientIsNullBeforeConnect() {
        var service = MqttClientService.builder("localhost", 1883).build();
        assertThat(service.getClient()).isNull();
    }

    @Test void testRecordTypesAreAccessible() {
        var result = MqttClientService.MqttResult.ok("test/topic", java.nio.ByteBuffer.wrap(new byte[]{1, 2, 3}));
        assertThat(result.success()).isTrue();
        assertThat(result.topic()).isEqualTo("test/topic");

        var error = MqttClientService.MqttResult.error("connection refused");
        assertThat(error.success()).isFalse();
        assertThat(error.topic()).isNull();
    }
}
