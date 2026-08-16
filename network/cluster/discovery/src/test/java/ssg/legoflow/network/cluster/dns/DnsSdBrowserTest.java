package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class DnsSdBrowserTest {

    private static final InetAddress LOCAL_ADDR;

    static {
        try {
            LOCAL_ADDR = InetAddress.getByName("127.0.0.1");
        } catch (java.net.UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void start_stop_lifecycle() throws Exception {
        DnsSdBrowser browser = new DnsSdBrowser("_http._tcp", "local", Duration.ofSeconds(5), LOCAL_ADDR);

        assertThat(browser.isBrowsing()).isFalse();
        browser.start();
        assertThat(browser.isBrowsing()).isTrue();

        Thread.sleep(500);

        browser.stop();
        assertThat(browser.isBrowsing()).isFalse();
    }

    @Test
    void services_emptyInitially() throws Exception {
        try (DnsSdBrowser browser = new DnsSdBrowser("_http._tcp", "local", Duration.ofSeconds(5), LOCAL_ADDR)) {
            assertThat(browser.services()).isEmpty();
        }
    }

    @Disabled("Requires multicast support on the loopback interface")
    @Test
    void discoversServices_fromResponder() throws Exception {
        List<DnsSdBrowser.DnsSdBrowserEvent> events = new CopyOnWriteArrayList<>();

        try (DnsSdBrowser browser = new DnsSdBrowser("_http._tcp", "local", Duration.ofSeconds(5), LOCAL_ADDR)) {
            browser.onEvent(events::add);
            browser.start();

            // Start a responder
            DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                    .serviceType("_http._tcp")
                    .domain("local")
                    .instanceName("BrowserTest")
                    .targetHostname("localhost")
                    .targetAddress(LOCAL_ADDR)
                    .port(8080)
                    .ttl(Duration.ofSeconds(30))
                    .build();

            try (MdnsResponder responder = new MdnsResponder(record, LOCAL_ADDR)) {
                responder.start();
                Thread.sleep(2000);

                // The browser should have received the announcement
                List<DnsSdBrowser.DnsSdBrowserEvent> added = events.stream()
                        .filter(e -> e.type() == DnsSdBrowser.DnsSdBrowserEvent.Type.ADDED)
                        .toList();

                assertThat(added).isNotEmpty();
            }
        }
    }

    @Test
    void close_stopsBrowsing() throws Exception {
        DnsSdBrowser browser = new DnsSdBrowser("_http._tcp", "local", Duration.ofSeconds(5), LOCAL_ADDR);
        browser.start();
        Thread.sleep(200);
        browser.close();
        assertThat(browser.isBrowsing()).isFalse();
    }

    @Test
    void doubleStart_isIdempotent() throws Exception {
        try (DnsSdBrowser browser = new DnsSdBrowser("_http._tcp", "local", Duration.ofSeconds(5), LOCAL_ADDR)) {
            browser.start();
            browser.start(); // Should not throw
        }
    }

    @Test
    void doubleStop_isIdempotent() throws Exception {
        try (DnsSdBrowser browser = new DnsSdBrowser("_http._tcp", "local", Duration.ofSeconds(5), LOCAL_ADDR)) {
            browser.start();
            browser.stop();
            browser.stop(); // Should not throw
        }
    }

    @Test
    void nullServiceType_throws() {
        assertThatThrownBy(() -> new DnsSdBrowser(null, "local", LOCAL_ADDR))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullDomain_throws() {
        assertThatThrownBy(() -> new DnsSdBrowser("_http._tcp", null, LOCAL_ADDR))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void eventRecord_typeAndRecord() {
        DnsSdBrowser.DnsSdBrowserEvent.Type type = DnsSdBrowser.DnsSdBrowserEvent.Type.ADDED;
        assertThat(type).isNotNull();
    }
}
