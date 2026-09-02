package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class MdnsResponderTest {

    private static final InetAddress LOCAL_ADDR;

    static {
        try {
            LOCAL_ADDR = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void serviceRecord_returnsConfiguredRecord() throws Exception {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("TestNode")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(8080)
                .ttl(Duration.ofSeconds(60))
                .build();

        try (MdnsResponder responder = new MdnsResponder(record)) {
            assertThat(responder.serviceRecord()).isSameAs(record);
            assertThat(responder.isRunning()).isFalse();
        }
    }

    @Test
    void start_stop_lifecycle() throws Exception {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("LifecycleTest")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(8081)
                .ttl(Duration.ofSeconds(60))
                .build();

        try (MdnsResponder responder = new MdnsResponder(record)) {
            assertThat(responder.isRunning()).isFalse();

            responder.start();
            assertThat(responder.isRunning()).isTrue();

            // Wait for announcement to be sent
            Thread.sleep(500);

            responder.stop();
            assertThat(responder.isRunning()).isFalse();
        }
    }

    @Test
    void close_callsStop() throws Exception {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("CloseTest")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(8082)
                .ttl(Duration.ofSeconds(60))
                .build();

        MdnsResponder responder = new MdnsResponder(record);
        responder.start();
        Thread.sleep(200);

        responder.close();
        assertThat(responder.isRunning()).isFalse();
    }

    @Test
    void nullServiceRecord_throws() {
        assertThatThrownBy(() -> new MdnsResponder(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void doubleStart_isIdempotent() throws Exception {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("Idempotent")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(8083)
                .ttl(Duration.ofSeconds(60))
                .build();

        try (MdnsResponder responder = new MdnsResponder(record)) {
            responder.start();
            Thread.sleep(200);
            responder.start(); // Should not throw
            assertThat(responder.isRunning()).isTrue();
        }
    }

    @Test
    void doubleStop_isIdempotent() throws Exception {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("DoubleStop")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(8084)
                .ttl(Duration.ofSeconds(60))
                .build();

        try (MdnsResponder responder = new MdnsResponder(record)) {
            responder.start();
            Thread.sleep(200);
            responder.stop();
            responder.stop(); // Should not throw
            assertThat(responder.isRunning()).isFalse();
        }
    }
}
