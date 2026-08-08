package ssg.legoflow.media.sip.server.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for SIP server service DP/DF compliance. */
class SipServerServiceTest {

    @Test void testBuilderCreatesService() {
        var service = SipServerService.builder(5060).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = SipServerService.builder(0).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = SipServerService.builder(0).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = SipServerService.builder(5060).priority(80).build();
        assertThat(service.getPriority()).isEqualTo(80);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = SipServerService.builder(5060).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetServerIsNullBeforeConnect() {
        var service = SipServerService.builder(0).build();
        assertThat(service.getServer()).isNull();
    }
}
