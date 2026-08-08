package ssg.legoflow.xmpp.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for XMPP client service DP/DF compliance. */
class XmppClientServiceTest {

    @Test void testBuilderCreatesService() {
        var service = XmppClientService.builder("xmpp.example.com", 5222).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = XmppClientService.builder("localhost", 5222).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = XmppClientService.builder("localhost", 5222).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = XmppClientService.builder("xmpp.local", 5222).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = XmppClientService.builder("localhost", 5222).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetClientIsNullBeforeConnect() {
        var service = XmppClientService.builder("localhost", 5222).build();
        assertThat(service.getClient()).isNull();
    }
}
