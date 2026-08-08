package ssg.legoflow.messaging.amqp.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for AMQP client service DP/DF compliance. */
class AmqpClientServiceTest {

    @Test void testBuilderCreatesService() {
        var service = AmqpClientService.builder("amqp.example.com", 5672).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = AmqpClientService.builder("localhost", 5672).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = AmqpClientService.builder("localhost", 5672).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = AmqpClientService.builder("amqp.local", 5672).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = AmqpClientService.builder("localhost", 5672).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetClientIsNullBeforeConnect() {
        var service = AmqpClientService.builder("localhost", 5672).build();
        assertThat(service.getClient()).isNull();
    }
}
