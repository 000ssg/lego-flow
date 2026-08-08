package ssg.legoflow.network.syslog.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for syslog sender service DP/DF compliance. */
class SyslogSenderServiceTest {

    @Test void testBuilderCreatesService() {
        var service = SyslogSenderService.builder("syslog.local", 514).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = SyslogSenderService.builder("localhost", 514).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = SyslogSenderService.builder("localhost", 514).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithUdpMode() {
        var service = SyslogSenderService.builder("syslog.local", 514)
                .mode(SyslogSenderService.TransportMode.UDP)
                .build();
        assertThat(service.getPriority()).isEqualTo(100); // default
    }

    @Test void testBuilderWithPriority() {
        var service = SyslogSenderService.builder("syslog.local", 514).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = SyslogSenderService.builder("localhost", 514).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetSenderIsNullBeforeConnect() {
        var service = SyslogSenderService.builder("localhost", 514).build();
        assertThat(service.getSender()).isNull();
    }

    @Test void testRecordTypesAreAccessible() {
        var result = SyslogSenderService.SyslogSendResult.ok("message sent");
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("message sent");

        var error = SyslogSenderService.SyslogSendResult.error("connection failed");
        assertThat(error.success()).isFalse();
        assertThat(error.message()).isEqualTo("connection failed");
    }
}
