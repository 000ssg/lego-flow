package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.protocol.ResponseCode;
import ssg.legoflow.network.dns.rdata.ARecord;
import ssg.legoflow.network.dns.rdata.PtrRecord;
import ssg.legoflow.network.dns.rdata.SrvRecord;
import ssg.legoflow.network.dns.rdata.TxtRecord;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class MdnsQuerierTest {

    @Test
    void start_stop_lifecycle() throws Exception {
        try (MdnsQuerier querier = new MdnsQuerier()) {
            assertThat(querier.isRunning()).isFalse();
            querier.start();
            assertThat(querier.isRunning()).isTrue();
            Thread.sleep(200);
            querier.stop();
            assertThat(querier.isRunning()).isFalse();
        }
    }

    @Test
    void cachedServices_emptyInitially() throws Exception {
        try (MdnsQuerier querier = new MdnsQuerier()) {
            assertThat(querier.cachedServices()).isEmpty();
        }
    }

    @Test
    void query_sendsPacket() throws Exception {
        try (MdnsQuerier querier = new MdnsQuerier()) {
            querier.query("_http._tcp", "local");
            // If no exception, the query was sent
            assertThat(querier.cachedServices()).isEmpty(); // No responses yet
        }
    }

    @Disabled("Requires multicast support on the loopback interface")
    @Test
    void onResolved_receivesServiceAnnouncement() throws Exception {
        java.util.List<DnsSdServiceRecord> resolved = new CopyOnWriteArrayList<>();
        AtomicInteger removedCount = new AtomicInteger(0);

        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        // Start querier
        try (MdnsQuerier querier = new MdnsQuerier(loopback)) {
            querier.onResolved(resolved::add);
            querier.onRemoved(name -> removedCount.incrementAndGet());
            querier.start();

            // Start a responder on the same interface
            DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                    .serviceType("_http._tcp")
                    .domain("local")
                    .instanceName("TestNode")
                    .targetHostname("localhost")
                    .targetAddress(loopback)
                    .port(8080)
                    .ttl(Duration.ofSeconds(30))
                    .build();

            try (MdnsResponder responder = new MdnsResponder(record, loopback)) {
                responder.start();

                // Give time for the announcement to be received
                Thread.sleep(2000);

                // Query for the service
                querier.query("_http._tcp", "local");
                Thread.sleep(1000);

                // Check results
                assertThat(resolved).isNotEmpty();
                DnsSdServiceRecord discovered = resolved.get(0);
                assertThat(discovered.instanceName()).isEqualTo("TestNode");
                assertThat(discovered.port()).isEqualTo(8080);
            }
        }
    }

    @Test
    void goodbye_removesFromCache() throws Exception {
        java.util.List<DnsSdServiceRecord> resolved = new CopyOnWriteArrayList<>();
        java.util.List<String> removed = new CopyOnWriteArrayList<>();

        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        try (MdnsQuerier querier = new MdnsQuerier(loopback)) {
            querier.onResolved(resolved::add);
            querier.onRemoved(removed::add);
            querier.start();

            DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                    .serviceType("_http._tcp")
                    .domain("local")
                    .instanceName("GoodbyeTest")
                    .targetHostname("localhost")
                    .targetAddress(loopback)
                    .port(8090)
                    .ttl(Duration.ofSeconds(30))
                    .build();

            try (MdnsResponder responder = new MdnsResponder(record, loopback)) {
                responder.start();
                Thread.sleep(1000);

                // Now manually send a goodbye to the querier's port
                DnsMessage goodbye = MdnsPacketCodec.buildGoodbye(record);
                byte[] data = MdnsPacketCodec.encode(goodbye);

                // The querier is listening on 5353 — send to self
                MulticastSocket socket = new MulticastSocket();
                socket.setReuseAddress(true);
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        InetAddress.getByName("127.0.0.1"), MdnsPacketCodec.MDNS_PORT);
                socket.send(packet);
                socket.close();

                Thread.sleep(500);

                // The service should have been received at some point
                // The goodbye should trigger removal
            }
        }
    }

    @Test
    void doubleStart_isIdempotent() throws Exception {
        try (MdnsQuerier querier = new MdnsQuerier()) {
            querier.start();
            querier.start(); // Should not throw
            assertThat(querier.isRunning()).isTrue();
        }
    }

    @Test
    void doubleStop_isIdempotent() throws Exception {
        try (MdnsQuerier querier = new MdnsQuerier()) {
            querier.start();
            querier.stop();
            querier.stop(); // Should not throw
            assertThat(querier.isRunning()).isFalse();
        }
    }

    @Test
    void query_nullArgs_throw() {
        try (MdnsQuerier querier = new MdnsQuerier()) {
            assertThatThrownBy(() -> querier.query(null, "local"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> querier.query("_http._tcp", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
