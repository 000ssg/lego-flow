package ssg.legoflow.media.sip.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for SIP client service DP/DF compliance. */
class SipClientServiceTest {

    @Test void testBuilderCreatesService() {
        var service = SipClientService.builder("sip.example.com", 5060).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = SipClientService.builder("localhost", 5060).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = SipClientService.builder("localhost", 5060).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = SipClientService.builder("sip.local", 5060).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = SipClientService.builder("localhost", 5060).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetUserAgentIsNullBeforeConnect() {
        var service = SipClientService.builder("localhost", 5060).build();
        assertThat(service.getUserAgent()).isNull();
    }
}
