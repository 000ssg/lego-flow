package ssg.legoflow.messaging.nats.server;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.protocol.NatsHeaders;
import ssg.legoflow.messaging.nats.server.auth.TokenAuthenticator;
import ssg.legoflow.messaging.nats.server.auth.UserPassAuthenticator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link NatsServer} with client integration.
 */
class NatsServerTest {

    @Test
    void testServerStartAndStop() throws IOException {
        try (var server = new NatsServer()) {
            server.start(0);
            assertThat(server.port()).isGreaterThan(0);
            assertThat(server.isRunning()).isTrue();
        }
    }

    @Test
    void testClientConnect() throws IOException {
        try (var server = new NatsServer()) {
            server.start(0);
            try (var client = new NatsClient("localhost", server.port())) {
                client.connect();
                assertThat(client.isConnected()).isTrue();
                assertThat(client.serverInfo()).isNotNull();
                assertThat(client.serverInfo().jetstream()).isTrue();
            }
        }
    }

    @Test
    void testPubSub() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            try (var sub = new NatsClient("localhost", server.port());
                 var pub = new NatsClient("localhost", server.port())) {
                sub.connect();
                pub.connect();

                sub.subscribe("test.topic", msg -> {
                    received.add(msg.dataAsString());
                    latch.countDown();
                });
                Thread.sleep(50);

                pub.publish("test.topic", "hello");
                latch.await(3, TimeUnit.SECONDS);

                assertThat(received).containsExactly("hello");
            }
        }
    }

    @Test
    void testPubSubMultipleMessages() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(3);

            try (var sub = new NatsClient("localhost", server.port());
                 var pub = new NatsClient("localhost", server.port())) {
                sub.connect();
                pub.connect();

                sub.subscribe("events", msg -> {
                    received.add(msg.dataAsString());
                    latch.countDown();
                });
                Thread.sleep(50);

                pub.publish("events", "e1");
                pub.publish("events", "e2");
                pub.publish("events", "e3");
                latch.await(3, TimeUnit.SECONDS);

                assertThat(received).containsExactly("e1", "e2", "e3");
            }
        }
    }

    @Test
    void testPubSubWildcard() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(2);

            try (var sub = new NatsClient("localhost", server.port());
                 var pub = new NatsClient("localhost", server.port())) {
                sub.connect();
                pub.connect();

                sub.subscribe("events.>", msg -> {
                    received.add(msg.subject() + ":" + msg.dataAsString());
                    latch.countDown();
                });
                Thread.sleep(50);

                pub.publish("events.login", "alice");
                pub.publish("events.logout", "bob");
                latch.await(3, TimeUnit.SECONDS);

                assertThat(received).containsExactlyInAnyOrder(
                        "events.login:alice", "events.logout:bob");
            }
        }
    }

    @Test
    void testPubSubWithHeaders() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);
            var latch = new CountDownLatch(1);
            var receivedHeaders = new NatsHeaders[1];

            try (var sub = new NatsClient("localhost", server.port());
                 var pub = new NatsClient("localhost", server.port())) {
                sub.connect();
                pub.connect();

                sub.subscribe("hdr.test", msg -> {
                    receivedHeaders[0] = msg.headers();
                    latch.countDown();
                });
                Thread.sleep(50);

                var headers = new NatsHeaders();
                headers.set("X-Type", "important");
                pub.publish("hdr.test", headers, "payload".getBytes());
                latch.await(3, TimeUnit.SECONDS);

                assertThat(receivedHeaders[0]).isNotNull();
                assertThat(receivedHeaders[0].getFirst("X-Type")).isEqualTo("important");
            }
        }
    }

    @Test
    void testRequestReply() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);
            try (var service = new NatsClient("localhost", server.port());
                 var requester = new NatsClient("localhost", server.port())) {
                service.connect();
                requester.connect();

                service.subscribe("echo", msg -> {
                    try {
                        service.publish(msg.replyTo(), msg.payload());
                    } catch (IOException e) { /* ignore */ }
                });
                Thread.sleep(50);

                var reply = requester.request("echo", "ping", Duration.ofSeconds(3));
                assertThat(reply).isNotNull();
                assertThat(reply.dataAsString()).isEqualTo("ping");
            }
        }
    }

    @Test
    void testRequestReplyTimeout() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);
            try (var client = new NatsClient("localhost", server.port())) {
                client.connect();

                // No service listening — should timeout
                var reply = client.request("no.service", "data", Duration.ofMillis(200));
                assertThat(reply).isNull();
            }
        }
    }

    @Test
    void testQueueGroup() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);
            var worker1Count = new AtomicInteger(0);
            var worker2Count = new AtomicInteger(0);
            var latch = new CountDownLatch(10);

            try (var w1 = new NatsClient("localhost", server.port());
                 var w2 = new NatsClient("localhost", server.port());
                 var pub = new NatsClient("localhost", server.port())) {
                w1.connect();
                w2.connect();
                pub.connect();

                w1.subscribe("tasks", "workers", msg -> {
                    worker1Count.incrementAndGet();
                    latch.countDown();
                });
                w2.subscribe("tasks", "workers", msg -> {
                    worker2Count.incrementAndGet();
                    latch.countDown();
                });
                Thread.sleep(50);

                for (int i = 0; i < 10; i++) {
                    pub.publish("tasks", "task-" + i);
                }
                latch.await(3, TimeUnit.SECONDS);

                // Both should get messages (round-robin)
                assertThat(worker1Count.get() + worker2Count.get()).isEqualTo(10);
                assertThat(worker1Count.get()).isGreaterThan(0);
                assertThat(worker2Count.get()).isGreaterThan(0);
            }
        }
    }

    @Test
    void testTokenAuth() throws IOException {
        try (var server = new NatsServer()) {
            server.setAuthenticator(new TokenAuthenticator("secret123"));
            server.start(0);

            // Correct token
            try (var client = new NatsClient("localhost", server.port(),
                    ConnectOptions.withDefaults("client").withToken("secret123"))) {
                client.connect();
                assertThat(client.isConnected()).isTrue();
            }
        }
    }

    @Test
    void testTokenAuthRejected() throws IOException {
        try (var server = new NatsServer()) {
            server.setAuthenticator(new TokenAuthenticator("secret123"));
            server.start(0);

            assertThatThrownBy(() -> {
                try (var client = new NatsClient("localhost", server.port(),
                        ConnectOptions.withDefaults("client").withToken("wrong"))) {
                    client.connect();
                }
            }).isInstanceOf(IOException.class);
        }
    }

    @Test
    void testUserPassAuth() throws IOException {
        try (var server = new NatsServer()) {
            var auth = new UserPassAuthenticator();
            auth.addUser("admin", "password");
            server.setAuthenticator(auth);
            server.start(0);

            try (var client = new NatsClient("localhost", server.port(),
                    ConnectOptions.withDefaults("client").withUserPass("admin", "password"))) {
                client.connect();
                assertThat(client.isConnected()).isTrue();
            }
        }
    }

    @Test
    void testUserPassAuthRejected() throws IOException {
        try (var server = new NatsServer()) {
            var auth = new UserPassAuthenticator();
            auth.addUser("admin", "password");
            server.setAuthenticator(auth);
            server.start(0);

            assertThatThrownBy(() -> {
                try (var client = new NatsClient("localhost", server.port(),
                        ConnectOptions.withDefaults("client").withUserPass("admin", "wrong"))) {
                    client.connect();
                }
            }).isInstanceOf(IOException.class);
        }
    }

    @Test
    void testMultipleClients() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);
            var received1 = new CopyOnWriteArrayList<String>();
            var received2 = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(2);

            try (var c1 = new NatsClient("localhost", server.port());
                 var c2 = new NatsClient("localhost", server.port());
                 var pub = new NatsClient("localhost", server.port())) {
                c1.connect();
                c2.connect();
                pub.connect();

                c1.subscribe("news", msg -> { received1.add(msg.dataAsString()); latch.countDown(); });
                c2.subscribe("news", msg -> { received2.add(msg.dataAsString()); latch.countDown(); });
                Thread.sleep(50);

                pub.publish("news", "breaking");
                latch.await(3, TimeUnit.SECONDS);

                assertThat(received1).containsExactly("breaking");
                assertThat(received2).containsExactly("breaking");
            }
        }
    }

    @Test
    void testUnsubscribe() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);
            var received = new CopyOnWriteArrayList<String>();

            try (var client = new NatsClient("localhost", server.port());
                 var pub = new NatsClient("localhost", server.port())) {
                client.connect();
                pub.connect();

                var sub = client.subscribe("topic", msg -> received.add(msg.dataAsString()));
                Thread.sleep(50);

                pub.publish("topic", "msg1");
                Thread.sleep(50);

                client.unsubscribe(sub);
                Thread.sleep(50);

                pub.publish("topic", "msg2");
                Thread.sleep(100);

                assertThat(received).containsExactly("msg1");
            }
        }
    }

    @Test
    void testClientDisconnect() throws IOException, InterruptedException {
        try (var server = new NatsServer()) {
            server.start(0);

            var client = new NatsClient("localhost", server.port());
            client.connect();
            assertThat(client.isConnected()).isTrue();

            client.close();
            Thread.sleep(100);
            assertThat(client.isConnected()).isFalse();
        }
    }

    @Test
    void testServerInfoFields() throws IOException {
        try (var server = new NatsServer()) {
            server.start(0);
            try (var client = new NatsClient("localhost", server.port())) {
                client.connect();
                var info = client.serverInfo();
                assertThat(info.serverId()).isNotEmpty();
                assertThat(info.serverName()).isEqualTo("lego-flow-nats");
                assertThat(info.headers()).isTrue();
                assertThat(info.proto()).isEqualTo(1);
            }
        }
    }
}
