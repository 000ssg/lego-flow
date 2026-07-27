package ssg.legoflow.network.syslog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ssg.legoflow.network.syslog.protocol.Facility;
import ssg.legoflow.network.syslog.protocol.Severity;
import ssg.legoflow.network.syslog.protocol.StructuredData;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;
import ssg.legoflow.network.syslog.transport.TcpCollector;
import ssg.legoflow.network.syslog.transport.UdpCollector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SyslogSender}.
 */
class SyslogSenderTest {

    @Test
    @Timeout(10)
    void testUdpSendSimple() throws Exception {
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (var collector = new UdpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = SyslogSender.udp("127.0.0.1", collector.localPort())) {
                sender.send(Facility.DAEMON, Severity.INFO, "test message");
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).hasSize(1);
            assertThat(received.getFirst().message()).isEqualTo("test message");
            assertThat(received.getFirst().timestamp()).isNotNull();
        }
    }

    @Test
    @Timeout(10)
    void testUdpSendWithStructuredData() throws Exception {
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (var collector = new UdpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = SyslogSender.udp("127.0.0.1", collector.localPort())) {
                var sd = StructuredData.builder("myID")
                        .param("key", "value")
                        .build();
                sender.send(Facility.USER, Severity.NOTICE, "structured",
                        List.of(sd));
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received.getFirst().structuredData()).hasSize(1);
        }
    }

    @Test
    @Timeout(10)
    void testTcpSend() throws Exception {
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (var collector = new TcpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = SyslogSender.tcp("127.0.0.1", collector.localPort())) {
                sender.send(Facility.AUTH, Severity.ERROR, "tcp test");
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received.getFirst().message()).isEqualTo("tcp test");
        }
    }

    @Test
    @Timeout(10)
    void testWithHostnameAndAppName() throws Exception {
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (var collector = new UdpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = SyslogSender.udp("127.0.0.1", collector.localPort())
                    .withHostname("myhost")
                    .withAppName("myapp")) {
                sender.send(Facility.USER, Severity.INFO, "named message");
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received.getFirst().hostname()).isEqualTo("myhost");
            assertThat(received.getFirst().appName()).isEqualTo("myapp");
        }
    }

    @Test
    @Timeout(10)
    void testSendPrebuiltMessage() throws Exception {
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (var collector = new UdpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });

            try (var sender = SyslogSender.udp("127.0.0.1", collector.localPort())) {
                var msg = SyslogMessage.builder(Facility.KERN, Severity.ALERT)
                        .message("prebuilt")
                        .build();
                sender.send(msg);
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received.getFirst().message()).isEqualTo("prebuilt");
        }
    }
}
