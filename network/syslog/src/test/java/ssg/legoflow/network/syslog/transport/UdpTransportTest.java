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
 * Tests for {@link UdpSender} and {@link UdpCollector}.
 */
class UdpTransportTest {

    @Test
    @Timeout(10)
    void testSendAndReceive() throws Exception {
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (var collector = new UdpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = new UdpSender("127.0.0.1", collector.localPort())) {
                var msg = SyslogMessage.builder(Facility.DAEMON, Severity.INFO)
                        .hostname("testhost")
                        .message("test message")
                        .build();
                sender.send(msg);
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).hasSize(1);
            assertThat(received.getFirst().facility()).isEqualTo(Facility.DAEMON);
            assertThat(received.getFirst().severity()).isEqualTo(Severity.INFO);
            assertThat(received.getFirst().message()).isEqualTo("test message");
        }
    }

    @Test
    @Timeout(10)
    void testSendMultipleMessages() throws Exception {
        int count = 5;
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(count);

        try (var collector = new UdpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = new UdpSender("127.0.0.1", collector.localPort())) {
                for (int i = 0; i < count; i++) {
                    var msg = SyslogMessage.builder(Facility.USER, Severity.DEBUG)
                            .message("msg-" + i)
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
        try (var collector = new UdpCollector(0)) {
            assertThat(collector.localPort()).isGreaterThan(0);
        }
    }

    @Test
    void testCollectorDoubleStartThrows() throws IOException {
        try (var collector = new UdpCollector(0)) {
            collector.start(msg -> {});
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class,
                    () -> collector.start(msg -> {}));
        }
    }
}
