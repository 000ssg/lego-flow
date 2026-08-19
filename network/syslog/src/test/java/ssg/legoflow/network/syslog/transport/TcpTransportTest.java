package ssg.legoflow.network.syslog.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ssg.legoflow.network.syslog.protocol.Facility;
import ssg.legoflow.network.syslog.protocol.Severity;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link TcpSender} and {@link TcpCollector}.
 */
class TcpTransportTest {

    @Test
    @Timeout(10)
    void testOctetCountingSendAndReceive() throws Exception {
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (var collector = new TcpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = new TcpSender("127.0.0.1", collector.localPort(),
                    FramingMode.OCTET_COUNTING)) {
                var msg = SyslogMessage.builder(Facility.AUTH, Severity.WARNING)
                        .hostname("tcphost")
                        .message("tcp test")
                        .build();
                sender.send(msg);
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).hasSize(1);
            assertThat(received.getFirst().message()).isEqualTo("tcp test");
        }
    }

    @Test
    @Timeout(10)
    void testNonTransparentSendAndReceive() throws Exception {
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (var collector = new TcpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = new TcpSender("127.0.0.1", collector.localPort(),
                    FramingMode.NON_TRANSPARENT)) {
                var msg = SyslogMessage.builder(Facility.MAIL, Severity.ERROR)
                        .message("non-transparent test")
                        .build();
                sender.send(msg);
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).hasSize(1);
            assertThat(received.getFirst().message()).isEqualTo("non-transparent test");
        }
    }

    @Test
    @Timeout(10)
    void testMultipleMessagesOctetCounting() throws Exception {
        int count = 3;
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(count);

        try (var collector = new TcpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = new TcpSender("127.0.0.1", collector.localPort())) {
                for (int i = 0; i < count; i++) {
                    var msg = SyslogMessage.builder(Facility.CRON, Severity.INFO)
                            .message("batch-" + i)
                            .build();
                    sender.send(msg);
                }
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).hasSize(count);
        }
    }

    @Test
    void testCollectorLocalPort() throws IOException {
        try (var collector = new TcpCollector(0)) {
            assertThat(collector.localPort()).isGreaterThan(0);
        }
    }

    @Test
    void testCollectorDoubleStartThrows() throws IOException {
        try (var collector = new TcpCollector(0)) {
            collector.start(msg -> {});
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class,
                    () -> collector.start(msg -> {}));
        }
    }
}
