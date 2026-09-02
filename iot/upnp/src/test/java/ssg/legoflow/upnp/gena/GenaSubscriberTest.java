package ssg.legoflow.upnp.gena;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
class GenaSubscriberTest {

    private HttpServer mockService;
    private GenaSubscriber subscriber;
    private URI eventSubUrl;

    @BeforeEach
    void setUp() throws IOException {
        // Mock UPnP service for handling SUBSCRIBE/UNSUBSCRIBE
        mockService = HttpServer.create(new InetSocketAddress(0), 0);
        mockService.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        mockService.start();

        eventSubUrl = URI.create("http://localhost:" + mockService.getAddress().getPort() + "/event");

        subscriber = new GenaSubscriber("localhost", 0);
        subscriber.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        subscriber.close();
        mockService.stop(0);
    }

    @Test
    void shouldSubscribeSuccessfully() throws Exception {
        // Given: a mock service that accepts subscriptions
        mockService.createContext("/event", exchange -> {
            if ("SUBSCRIBE".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("SID", "uuid:sub-001");
                exchange.getResponseHeaders().add("TIMEOUT", "Second-300");
                exchange.sendResponseHeaders(200, 0);
            }
            exchange.close();
        });

        // When: subscribing
        var subscription = subscriber.subscribe(eventSubUrl, "ContentDirectory", Duration.ofSeconds(300));

        // Then: subscription is active
        assertThat(subscription.sid()).isEqualTo("uuid:sub-001");
        assertThat(subscription.serviceId()).isEqualTo("ContentDirectory");
        assertThat(subscription.isExpired()).isFalse();
        assertThat(subscriber.getSubscriptions()).containsKey("uuid:sub-001");
    }

    @Test
    void shouldRenewSubscription() throws Exception {
        // Given: an active subscription
        mockService.createContext("/event", exchange -> {
            if ("SUBSCRIBE".equals(exchange.getRequestMethod())) {
                var sid = exchange.getRequestHeaders().getFirst("SID");
                if (sid != null) {
                    // Renewal request
                    exchange.getResponseHeaders().add("SID", sid);
                    exchange.getResponseHeaders().add("TIMEOUT", "Second-600");
                } else {
                    // Initial subscription
                    exchange.getResponseHeaders().add("SID", "uuid:sub-002");
                    exchange.getResponseHeaders().add("TIMEOUT", "Second-300");
                }
                exchange.sendResponseHeaders(200, 0);
            }
            exchange.close();
        });

        var subscription = subscriber.subscribe(eventSubUrl, "CDS", Duration.ofSeconds(300));

        // When: renewing
        var renewed = subscriber.renew(subscription);

        // Then: subscription is renewed
        assertThat(renewed.sid()).isEqualTo("uuid:sub-002");
        assertThat(renewed.expiresAt()).isAfter(subscription.expiresAt());
    }

    @Test
    void shouldUnsubscribeSuccessfully() throws Exception {
        // Given: an active subscription
        var unsubscribeCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
        mockService.createContext("/event", exchange -> {
            if ("SUBSCRIBE".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("SID", "uuid:sub-003");
                exchange.getResponseHeaders().add("TIMEOUT", "Second-300");
                exchange.sendResponseHeaders(200, 0);
            } else if ("UNSUBSCRIBE".equals(exchange.getRequestMethod())) {
                unsubscribeCalled.set(true);
                exchange.sendResponseHeaders(200, 0);
            }
            exchange.close();
        });

        var subscription = subscriber.subscribe(eventSubUrl, "CDS", Duration.ofSeconds(300));

        // When: unsubscribing
        subscriber.unsubscribe(subscription);

        // Then: subscription is removed and UNSUBSCRIBE was sent
        assertThat(unsubscribeCalled.get()).isTrue();
        assertThat(subscriber.getSubscriptions()).doesNotContainKey("uuid:sub-003");
    }

    @Test
    void shouldReceiveEventNotification() throws Exception {
        // Given: an active subscriber with a listener
        mockService.createContext("/event", exchange -> {
            exchange.getResponseHeaders().add("SID", "uuid:sub-004");
            exchange.getResponseHeaders().add("TIMEOUT", "Second-300");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        var events = Collections.synchronizedList(new ArrayList<EventMessage>());
        var latch = new CountDownLatch(1);
        subscriber.addListener(event -> {
            events.add(event);
            latch.countDown();
        });

        subscriber.subscribe(eventSubUrl, "CDS", Duration.ofSeconds(300));

        // When: sending a NOTIFY to the callback
        var eventXml = """
                <?xml version="1.0"?>
                <e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
                <e:property><SystemUpdateID>99</SystemUpdateID></e:property>
                </e:propertyset>
                """;

        var callbackUrl = "http://localhost:" + subscriber.getCallbackPort() + "/callback/CDS";
        var notifyRequest = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .method("NOTIFY", HttpRequest.BodyPublishers.ofString(eventXml))
                .header("Content-Type", "text/xml")
                .header(GenaConstants.HEADER_SID, "uuid:sub-004")
                .header(GenaConstants.HEADER_SEQ, "0")
                .header(GenaConstants.HEADER_NT, GenaConstants.NT_UPNP_EVENT)
                .header(GenaConstants.HEADER_NTS, GenaConstants.NTS_PROPCHANGE)
                .build();

        HttpClient.newHttpClient().send(notifyRequest, HttpResponse.BodyHandlers.ofString());

        // Then: the event is received by the listener
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().sid()).isEqualTo("uuid:sub-004");
        assertThat(events.getFirst().changedVariables()).containsEntry("SystemUpdateID", "99");
    }

    @Test
    void shouldStartAndStopCleanly() throws IOException {
        // Given: a fresh subscriber
        var sub = new GenaSubscriber("localhost", 0);

        // When: starting and stopping
        sub.start();
        assertThat(sub.isRunning()).isTrue();

        sub.stop();
        assertThat(sub.isRunning()).isFalse();

        sub.close();
    }

    @Test
    void shouldFailSubscriptionOnHttpError() {
        // Given: a service that rejects subscriptions
        mockService.createContext("/event", exchange -> {
            exchange.sendResponseHeaders(412, 0);
            exchange.close();
        });

        // When/Then: subscribing throws
        assertThatIOException()
                .isThrownBy(() -> subscriber.subscribe(eventSubUrl, "CDS", Duration.ofSeconds(300)))
                .withMessageContaining("SUBSCRIBE failed");
    }

    @Test
    void shouldRemoveListener() throws Exception {
        // Given: a listener
        var events = Collections.synchronizedList(new ArrayList<EventMessage>());
        GenaListener listener = events::add;
        subscriber.addListener(listener);

        // When: removing the listener
        subscriber.removeListener(listener);

        // And: sending a NOTIFY
        var eventXml = """
                <?xml version="1.0"?>
                <e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
                <e:property><Var>val</Var></e:property>
                </e:propertyset>
                """;

        var callbackUrl = "http://localhost:" + subscriber.getCallbackPort() + "/callback/test";
        var notifyRequest = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .method("NOTIFY", HttpRequest.BodyPublishers.ofString(eventXml))
                .header(GenaConstants.HEADER_SID, "uuid:sub-test")
                .header(GenaConstants.HEADER_SEQ, "0")
                .build();

        HttpClient.newHttpClient().send(notifyRequest, HttpResponse.BodyHandlers.ofString());
        Thread.sleep(200);

        // Then: no events received
        assertThat(events).isEmpty();
    }

    @Test
    void shouldTrackSubscriptionExpiry() throws Exception {
        // Given: a service that grants a very short subscription
        mockService.createContext("/event", exchange -> {
            exchange.getResponseHeaders().add("SID", "uuid:sub-005");
            exchange.getResponseHeaders().add("TIMEOUT", "Second-2");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        // When: subscribing with short timeout
        var subscription = subscriber.subscribe(eventSubUrl, "CDS", Duration.ofSeconds(2));

        // Then: initially not expired
        assertThat(subscription.isExpired()).isFalse();

        // And: after enough time, should be expired
        Thread.sleep(2500);
        assertThat(subscription.isExpired()).isTrue();
    }
}
