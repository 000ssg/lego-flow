package ssg.legoflow.ftp.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for FTP client service DP/DF compliance. */
class FtpClientServiceTest {

    @Test void testBuilderCreatesService() {
        var service = FtpClientService.builder("ftp.example.com", 21).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = FtpClientService.builder("localhost", 21).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = FtpClientService.builder("localhost", 21).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = FtpClientService.builder("ftp.local", 21).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = FtpClientService.builder("localhost", 21).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetClientIsNullBeforeConnect() {
        var service = FtpClientService.builder("localhost", 21).build();
        assertThat(service.getClient()).isNull();
    }

    @Test void testRecordTypesAreAccessible() {
        var result = FtpClientService.FtpResult.ok("list", java.nio.ByteBuffer.wrap(new byte[]{1, 2, 3}));
        assertThat(result.success()).isTrue();
        assertThat(result.operation()).isEqualTo("list");

        var error = FtpClientService.FtpResult.error("connection refused");
        assertThat(error.success()).isFalse();
        assertThat(error.operation()).isNull();
    }
}
