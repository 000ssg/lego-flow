package ssg.legoflow.media.rtsp.server.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for RTSP server service DP/DF compliance. */
class RtspServerServiceTest {

    @Test void testBuilderCreatesService() {
        var service = RtspServerService.builder(8554).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = RtspServerService.builder(0).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = RtspServerService.builder(0).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = RtspServerService.builder(8554).priority(80).build();
        assertThat(service.getPriority()).isEqualTo(80);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = RtspServerService.builder(8554).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetServerIsNullBeforeConnect() {
        var service = RtspServerService.builder(0).build();
        assertThat(service.getServer()).isNull();
    }
}
