package ssg.legoflow.coap.observe;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObserveRegistry}.
 *
 * @since 1.0.0
 */
class ObserveRegistryTest {

    private ObserveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ObserveRegistry();
    }

    @Test
    void testRegister() {
        var token = new byte[]{0x01};
        var observer = new InetSocketAddress("localhost", 5683);

        var relation = registry.register(token, "/sensors/temp", observer);

        assertThat(relation).isNotNull();
        assertThat(relation.isActive()).isTrue();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void testDeregister() {
        var token = new byte[]{0x01};
        registry.register(token, "/sensors/temp", new InetSocketAddress(5683));

        registry.deregister(token);

        assertThat(registry.size()).isZero();
        assertThat(registry.getObservers("/sensors/temp")).isEmpty();
    }

    @Test
    void testGetObservers() {
        registry.register(new byte[]{0x01}, "/sensors/temp", new InetSocketAddress("host1", 5683));
        registry.register(new byte[]{0x02}, "/sensors/temp", new InetSocketAddress("host2", 5683));
        registry.register(new byte[]{0x03}, "/sensors/humidity", new InetSocketAddress("host3", 5683));

        var tempObservers = registry.getObservers("/sensors/temp");
        assertThat(tempObservers).hasSize(2);

        var humidityObservers = registry.getObservers("/sensors/humidity");
        assertThat(humidityObservers).hasSize(1);
    }

    @Test
    void testNotifyObservers() {
        registry.register(new byte[]{0x01}, "/sensors/temp", new InetSocketAddress("host1", 5683));
        registry.register(new byte[]{0x02}, "/sensors/temp", new InetSocketAddress("host2", 5683));

        var notification = CoapMessage.builder()
                .type(CoapType.NON_CONFIRMABLE)
                .code(CoapCode.CONTENT)
                .payload("22.5")
                .build();

        var entries = registry.notifyObservers("/sensors/temp", notification);

        assertThat(entries).hasSize(2);
        for (var entry : entries) {
            assertThat(entry.notification().code()).isEqualTo(CoapCode.CONTENT);
            assertThat(entry.notification().getPayloadString()).isEqualTo("22.5");
        }
    }

    @Test
    void testReregisterSameToken() {
        var token = new byte[]{0x01};
        registry.register(token, "/sensors/temp", new InetSocketAddress(5683));
        registry.register(token, "/sensors/humidity", new InetSocketAddress(5684));

        // Should replace the old registration
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.getObservers("/sensors/temp")).isEmpty();
        assertThat(registry.getObservers("/sensors/humidity")).hasSize(1);
    }

    @Test
    void testClear() {
        registry.register(new byte[]{0x01}, "/path1", new InetSocketAddress(5683));
        registry.register(new byte[]{0x02}, "/path2", new InetSocketAddress(5684));

        registry.clear();

        assertThat(registry.size()).isZero();
    }

    @Test
    void testDeregisteredNotIncludedInObservers() {
        var token = new byte[]{0x01};
        registry.register(token, "/path", new InetSocketAddress(5683));
        registry.deregister(token);

        assertThat(registry.getObservers("/path")).isEmpty();
    }

    @Test
    void testConcurrentRegistrations() throws InterruptedException {
        int threadCount = 10;
        var latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            Thread.ofVirtual().start(() -> {
                registry.register(new byte[]{(byte) index}, "/path", new InetSocketAddress(5683 + index));
                latch.countDown();
            });
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(registry.size()).isEqualTo(threadCount);
        assertThat(registry.getObservers("/path")).hasSize(threadCount);
    }
}
