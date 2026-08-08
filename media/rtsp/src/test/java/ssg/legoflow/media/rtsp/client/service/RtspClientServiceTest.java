package ssg.legoflow.media.rtsp.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for RTSP client service DP/DF compliance. */
class RtspClientServiceTest {

    @Test void testBuilderCreatesService() {
        var service = RtspClientService.builder("localhost", 8554).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = RtspClientService.builder("localhost", 8554).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = RtspClientService.builder("localhost", 8554).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = RtspClientService.builder("rtsp.local", 8554).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = RtspClientService.builder("localhost", 8554).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetClientIsNullBeforeConnect() {
        var service = RtspClientService.builder("localhost", 8554).build();
        assertThat(service.getClient()).isNull();
    }
}
