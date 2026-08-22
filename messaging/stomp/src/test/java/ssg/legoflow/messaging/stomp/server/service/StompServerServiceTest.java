package ssg.legoflow.messaging.stomp.server.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
/** Tests for STOMP server service DP/DF compliance. */
class StompServerServiceTest {

    @Test
    void testBuilderCreatesService() {
        var service = StompServerService.builder(61613).build();
        assertThat(service).isNotNull();
    }

    @Test
    void testInitialStateIsDisconnected() {
        var service = StompServerService.builder(0).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    void testDisconnectBeforeConnectDoesNotThrow() {
        var service = StompServerService.builder(0).build();
        try {
            service.disconnect(service.getServiceContext());
        } catch (Exception e) {
            fail("should not throw: " + e);
        }
    }

    @Test
    void testBuilderWithCustomPriority() {
        var service = StompServerService.builder(61613)
                .priority(50)
                .build();
        assertThat(service.getPriority()).isEqualTo(50);
    }

    @Test
    void testChannelHandlerCanBeCreated() {
        var service = StompServerService.builder(61613).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    void testGetServerIsNullBeforeConnect() {
        var service = StompServerService.builder(0).build();
        assertThat(service.getServer()).isNull();
    }
}
