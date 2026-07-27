package ssg.legoflow.upnp.ssdp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class SsdpServiceTest {

    private SsdpService service;

    @BeforeEach
    void setUp() throws Exception {
        var networkInterface = java.net.NetworkInterface.getByIndex(1);
        if (networkInterface == null) {
            var interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                var ni = interfaces.nextElement();
                if (ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
                    networkInterface = ni;
                    break;
                }
            }
        }
        if (networkInterface == null) {
            networkInterface = java.net.NetworkInterface.getByIndex(1);
        }
        // Use the package-private constructor to avoid binding to the real multicast port
        var channel = java.nio.channels.DatagramChannel.open(java.net.StandardProtocolFamily.INET);
        channel.setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true);
        channel.bind(new InetSocketAddress(0));
        service = new SsdpService(channel, networkInterface);
    }

    @AfterEach
    void tearDown() throws Exception {
        service.close();
    }

    @Test
    void shouldStartAndStop() {
        // Given: a fresh service

        // When: starting
        service.start();

        // Then: service is running
        assertThat(service.isRunning()).isTrue();

        // When: stopping
        service.stop();

        // Then: service is stopped
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void shouldNotifyListenerOnAliveMessage() throws Exception {
        // Given: a listener is registered
        var events = Collections.synchronizedList(new ArrayList<SsdpEvent>());
        var latch = new CountDownLatch(1);
        service.addListener(event -> {
            events.add(event);
            latch.countDown();
        });

        // When: processing an alive message
        var message = SsdpMessage.alive(
                "http://192.168.1.100:8080/desc.xml",
                "upnp:rootdevice",
                "uuid:device-1::upnp:rootdevice",
                "Server/1.0", 1800
        );
        service.processMessage(message);

        // Then: listener receives a DeviceDiscovered event
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(SsdpEvent.DeviceDiscovered.class);
        var discovered = (SsdpEvent.DeviceDiscovered) events.getFirst();
        assertThat(discovered.usn()).isEqualTo("uuid:device-1::upnp:rootdevice");
        assertThat(discovered.location()).isEqualTo("http://192.168.1.100:8080/desc.xml");
    }

    @Test
    void shouldNotifyListenerOnByebyeMessage() throws Exception {
        // Given: a device is cached and a listener is registered
        var aliveMsg = SsdpMessage.alive(
                "http://192.168.1.100:8080/desc.xml", "upnp:rootdevice",
                "uuid:device-1::upnp:rootdevice", "Server/1.0", 1800
        );
        service.processMessage(aliveMsg);

        var events = Collections.synchronizedList(new ArrayList<SsdpEvent>());
        var latch = new CountDownLatch(1);
        service.addListener(event -> {
            if (event instanceof SsdpEvent.DeviceLost) {
                events.add(event);
                latch.countDown();
            }
        });

        // When: processing a byebye message
        var byebye = SsdpMessage.byebye("upnp:rootdevice", "uuid:device-1::upnp:rootdevice");
        service.processMessage(byebye);

        // Then: listener receives a DeviceLost event
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(events).hasSize(1);
        var lost = (SsdpEvent.DeviceLost) events.getFirst();
        assertThat(lost.usn()).isEqualTo("uuid:device-1::upnp:rootdevice");
    }

    @Test
    void shouldCacheDeviceOnAlive() {
        // Given: no devices in cache

        // When: processing an alive message
        var message = SsdpMessage.alive(
                "http://192.168.1.100:8080/desc.xml", "upnp:rootdevice",
                "uuid:device-1::upnp:rootdevice", "Server/1.0", 1800
        );
        service.processMessage(message);

        // Then: device is in the cache
        var cache = service.getDeviceCache();
        assertThat(cache).containsKey("uuid:device-1::upnp:rootdevice");
        assertThat(cache.get("uuid:device-1::upnp:rootdevice").location())
                .isEqualTo("http://192.168.1.100:8080/desc.xml");
    }

    @Test
    void shouldRemoveDeviceFromCacheOnByebye() {
        // Given: a device is in the cache
        var message = SsdpMessage.alive(
                "http://192.168.1.100:8080/desc.xml", "upnp:rootdevice",
                "uuid:device-1::upnp:rootdevice", "Server/1.0", 1800
        );
        service.processMessage(message);
        assertThat(service.getDeviceCache()).isNotEmpty();

        // When: processing byebye
        var byebye = SsdpMessage.byebye("upnp:rootdevice", "uuid:device-1::upnp:rootdevice");
        service.processMessage(byebye);

        // Then: device is removed from cache
        assertThat(service.getDeviceCache()).doesNotContainKey("uuid:device-1::upnp:rootdevice");
    }

    @Test
    void shouldCacheDeviceWithCorrectExpiry() {
        // Given/When: processing an alive with max-age=60
        var message = SsdpMessage.alive(
                "http://host/desc.xml", "upnp:rootdevice",
                "uuid:dev-1::upnp:rootdevice", "Server/1.0", 60
        );
        service.processMessage(message);

        // Then: expiry is approximately 60 seconds from now
        var cached = service.getDeviceCache().get("uuid:dev-1::upnp:rootdevice");
        assertThat(cached).isNotNull();
        assertThat(cached.expiresAt()).isAfter(Instant.now().plusSeconds(50));
        assertThat(cached.expiresAt()).isBefore(Instant.now().plusSeconds(70));
    }

    @Test
    void shouldHandleSearchResponse() throws Exception {
        // Given: a listener registered
        var events = Collections.synchronizedList(new ArrayList<SsdpEvent>());
        var latch = new CountDownLatch(1);
        service.addListener(event -> {
            events.add(event);
            latch.countDown();
        });

        // When: processing a search response
        var response = SsdpMessage.searchResponse(
                "http://192.168.1.50:9000/desc.xml", "upnp:rootdevice",
                "uuid:dev-2::upnp:rootdevice", "Server/1.0", 900
        );
        service.processMessage(response);

        // Then: listener receives SearchResponse event
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(events.getFirst()).isInstanceOf(SsdpEvent.SearchResponse.class);
    }

    @Test
    void shouldNotDuplicateEventOnRefresh() {
        // Given: a device already in cache
        var events = Collections.synchronizedList(new ArrayList<SsdpEvent>());
        service.addListener(events::add);

        var message = SsdpMessage.alive(
                "http://host/desc.xml", "upnp:rootdevice",
                "uuid:dev-1::upnp:rootdevice", "Server/1.0", 1800
        );
        service.processMessage(message);
        assertThat(events).hasSize(1);

        // When: processing a second alive for the same device
        service.processMessage(message);

        // Then: no additional DeviceDiscovered event
        assertThat(events).hasSize(1);
    }

    @Test
    void shouldRemoveListenerSuccessfully() throws Exception {
        // Given: a listener
        var events = Collections.synchronizedList(new ArrayList<SsdpEvent>());
        SsdpListener listener = events::add;
        service.addListener(listener);

        // When: removing the listener and processing a message
        service.removeListener(listener);
        var message = SsdpMessage.alive(
                "http://host/desc.xml", "upnp:rootdevice",
                "uuid:dev-1::upnp:rootdevice", "Server/1.0", 1800
        );
        service.processMessage(message);

        // Then: listener receives no events
        Thread.sleep(100);
        assertThat(events).isEmpty();
    }

    @Test
    void shouldHandleMultipleDevicesInCache() {
        // Given/When: processing alive messages for three devices
        for (int i = 1; i <= 3; i++) {
            var message = SsdpMessage.alive(
                    "http://host" + i + "/desc.xml", "upnp:rootdevice",
                    "uuid:dev-" + i + "::upnp:rootdevice", "Server/1.0", 1800
            );
            service.processMessage(message);
        }

        // Then: all three devices are in cache
        assertThat(service.getDeviceCache()).hasSize(3);
        assertThat(service.getDeviceCache()).containsKeys(
                "uuid:dev-1::upnp:rootdevice",
                "uuid:dev-2::upnp:rootdevice",
                "uuid:dev-3::upnp:rootdevice"
        );
    }
}
