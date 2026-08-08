package ssg.legoflow.messaging.amqp.server.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for AMQP container service DP/DF compliance. */
class AmqpContainerServiceTest {

    @Test void testBuilderCreatesService() {
        var service = AmqpContainerService.builder(5672).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = AmqpContainerService.builder(0).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = AmqpContainerService.builder(0).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = AmqpContainerService.builder(5672).priority(80).build();
        assertThat(service.getPriority()).isEqualTo(80);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = AmqpContainerService.builder(5672).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetContainerIsNullBeforeConnect() {
        var service = AmqpContainerService.builder(0).build();
        assertThat(service.getContainer()).isNull();
    }

    @Test void testRecordTypesAreAccessible() {
        var result = AmqpContainerService.AmqpResult.ok("queue/test", java.nio.ByteBuffer.wrap(new byte[]{1, 2, 3}));
        assertThat(result.success()).isTrue();
        assertThat(result.address()).isEqualTo("queue/test");

        var error = AmqpContainerService.AmqpResult.error("not connected");
        assertThat(error.success()).isFalse();
        assertThat(error.address()).isNull();
    }
}
