package ssg.legoflow.network.syslog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ssg.legoflow.network.syslog.protocol.Facility;
import ssg.legoflow.network.syslog.protocol.Severity;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;
import ssg.legoflow.network.syslog.transport.UdpSender;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link SyslogCollector}.
 */
class SyslogCollectorTest {

    @Test
    @Timeout(10)
    void testUdpCollector() throws Exception {
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (var collector = SyslogCollector.udp(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = new UdpSender("127.0.0.1", collector.udpPort())) {
                var msg = SyslogMessage.builder(Facility.USER, Severity.INFO)
                        .message("collector test")
                        .build();
                sender.send(msg);
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).hasSize(1);
        }
    }

    @Test
    void testBuilderRequiresTransport() {
        assertThatThrownBy(() -> SyslogCollector.builder().build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testUdpPort() throws IOException {
        try (var collector = SyslogCollector.udp(0)) {
            assertThat(collector.udpPort()).isGreaterThan(0);
            assertThat(collector.tcpPort()).isEqualTo(-1);
        }
    }

    @Test
    void testTcpPort() throws IOException {
        try (var collector = SyslogCollector.tcp(0)) {
            assertThat(collector.tcpPort()).isGreaterThan(0);
            assertThat(collector.udpPort()).isEqualTo(-1);
        }
    }

    @Test
    void testBuilderBothTransports() throws IOException {
        try (var collector = SyslogCollector.builder()
                .udp(0)
                .tcp(0)
                .build()) {
            assertThat(collector.udpPort()).isGreaterThan(0);
            assertThat(collector.tcpPort()).isGreaterThan(0);
        }
    }
}
