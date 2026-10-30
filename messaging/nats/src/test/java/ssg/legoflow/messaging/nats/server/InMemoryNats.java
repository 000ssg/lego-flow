package ssg.legoflow.messaging.nats.server;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.protocol.ServerInfo;
import ssg.legoflow.messaging.nats.server.auth.Authenticator;
import ssg.legoflow.messaging.nats.transport.InMemoryNatsTransport;

import java.io.IOException;

/**
 * In-memory fixture wiring the headless {@link NatsServer} core to a {@link NatsClient}
 * over an {@link InMemoryNatsTransport} pair — no sockets, no ports, deterministic.
 *
 * <p>This is the test-side equivalent of the production path (manager +
 * {@code PipelineNatsTransport}); the transport-injection seam exists precisely so the
 * whole protocol can be exercised without TCP. The server-side end of the pair is fed to
 * {@link NatsServer#handleConnection} (the same seam the production service uses); the
 * client end is passed to {@link NatsClient}.
 */
public final class InMemoryNats {

    private InMemoryNats() {}

    /** A running, headless NATS server core. Close it when done. */
    public static NatsServer server() {
        var server = new NatsServer();
        server.start();
        return server;
    }

    /**
     * Feeds one end of a fresh in-memory pair to the server; returns the client-side transport
     * so the test can build its {@link NatsClient}.
     */
    public static InMemoryNatsTransport[] pair(NatsServer server) {
        var pair = InMemoryNatsTransport.createPair();
        server.handleConnection(pair[0]); // server side: run the connection loop on a virtual thread
        return pair;
    }

    /** A connected NATS client over the given server-side-wired pair. */
    public static NatsClient client(NatsServer server, ConnectOptions options) throws IOException {
        var pair = pair(server);
        var client = new NatsClient(pair[1], options);
        client.connect();
        return client;
    }

    /** A connected NATS client with default options over a fresh in-memory pair. */
    public static NatsClient client(NatsServer server) throws IOException {
        var pair = pair(server);
        var client = new NatsClient(pair[1]);
        client.connect();
        return client;
    }
}
