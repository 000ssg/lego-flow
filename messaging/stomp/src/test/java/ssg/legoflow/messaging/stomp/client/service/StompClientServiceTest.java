package ssg.legoflow.messaging.stomp.client.service;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/** Tests for STOMP client service DP/DF compliance. */
class StompClientServiceTest {

    @Test
    void testBuilderCreatesService() {
        var service = StompClientService.builder("localhost", 61613).build();
        assertThat(service).isNotNull();
    }

    @Test
    void testInitialStateIsDisconnected() {
        var service = StompClientService.builder("localhost", 61613).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    void testDisconnectBeforeConnectDoesNotThrow() {
        var service = StompClientService.builder("localhost", 61613).build();
        try {
            service.disconnect(service.getServiceContext());
        } catch (Exception e) {
            fail("should not throw: " + e);
        }
    }

    @Test
    void testBuilderWithDependencies() {
        var service = StompClientService.builder("stomp.local", 61613)
                .dependencies("network-service")
                .build();
        assertThat(service.getDependencies()).contains("network-service");
    }

    @Test
    void testChannelHandlerCanBeCreated() {
        var service = StompClientService.builder("localhost", 61613).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    void testGetClientIsNullBeforeConnect() {
        var service = StompClientService.builder("localhost", 61613).build();
        assertThat(service.getClient()).isNull();
    }
}
