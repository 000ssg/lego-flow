package ssg.legoflow.ssh.client.service;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/** Tests for SSH client service DP/DF compliance. */
class SshClientServiceTest {

    @Test
    void testBuilderCreatesService() {
        var service = SshClientService.builder("localhost", 22).build();
        assertThat(service).isNotNull();
    }

    @Test
    void testInitialStateIsDisconnected() {
        var service = SshClientService.builder("localhost", 22).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    void testDisconnectBeforeConnectDoesNotThrow() {
        var service = SshClientService.builder("localhost", 22).build();
        try {
            service.disconnect(service.getServiceContext());
        } catch (Exception e) {
            fail("should not throw: " + e);
        }
    }

    @Test
    void testBuilderWithPriority() {
        var service = SshClientService.builder("ssh.example.com", 2222)
                .priority(90)
                .build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test
    void testChannelHandlerCanBeCreated() {
        var service = SshClientService.builder("localhost", 22).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    void testGetClientIsNullBeforeConnect() {
        var service = SshClientService.builder("localhost", 22).build();
        assertThat(service.getClient()).isNull();
    }

    @Test
    void testRecordTypesAreAccessible() {
        var result = SshClientService.SshSessionResult.ok("test", java.nio.ByteBuffer.wrap(new byte[]{1, 2, 3}));
        assertThat(result.success()).isTrue();
        assertThat(result.channel()).isEqualTo("test");

        var error = SshClientService.SshSessionResult.error("connection failed");
        assertThat(error.success()).isFalse();
        assertThat(error.channel()).isNull();
    }
}
