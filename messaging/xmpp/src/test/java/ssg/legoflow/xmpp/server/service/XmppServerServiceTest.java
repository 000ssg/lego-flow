package ssg.legoflow.xmpp.server.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for XMPP server service DP/DF compliance. */
class XmppServerServiceTest {

    @Test void testBuilderCreatesService() {
        var service = XmppServerService.builder(5222).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = XmppServerService.builder(0).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = XmppServerService.builder(0).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = XmppServerService.builder(5222).priority(80).build();
        assertThat(service.getPriority()).isEqualTo(80);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = XmppServerService.builder(5222).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetServerIsNullBeforeConnect() {
        var service = XmppServerService.builder(0).build();
        assertThat(service.getServer()).isNull();
    }
}
