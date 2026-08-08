package ssg.legoflow.coap.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for CoAP client service DP/DF compliance. */
class CoapClientServiceTest {

    @Test void testBuilderCreatesService() {
        var service = CoapClientService.builder("localhost", 5683).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = CoapClientService.builder("localhost", 5683).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = CoapClientService.builder("localhost", 5683).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = CoapClientService.builder("coap.local", 5683).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = CoapClientService.builder("localhost", 5683).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetClientIsNullBeforeConnect() {
        var service = CoapClientService.builder("localhost", 5683).build();
        assertThat(service.getClient()).isNull();
    }
}
