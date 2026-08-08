package ssg.legoflow.coap.server.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for CoAP server service DP/DF compliance. */
class CoapServerServiceTest {

    @Test void testBuilderCreatesService() {
        var service = CoapServerService.builder().build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = CoapServerService.builder().build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = CoapServerService.builder().build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = CoapServerService.builder().priority(80).build();
        assertThat(service.getPriority()).isEqualTo(80);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = CoapServerService.builder().build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetServerIsNullBeforeConnect() {
        var service = CoapServerService.builder().build();
        assertThat(service.getServer()).isNull();
    }
}
