package ssg.legoflow.messaging.kafka.demo;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.service.KafkaClientService;
import ssg.legoflow.messaging.kafka.transport.InMemoryKafkaTransport;
import ssg.legoflow.messaging.kafka.transport.KafkaTransport;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;

import java.io.IOException;

/**
 * One client connection to the demo broker, exposing the transport to build a
 * {@code KafkaProducer}/{@code KafkaConsumer}/{@code KafkaAdminClient} over. One instance = one
 * connection, matching the production client's connection-per-instance model.
 *
 * <p><b>Two backends</b> (the only code that changes when switching, mirroring the module's
 * in-house / external-broker split):</p>
 * <ul>
 *   <li><b>In-memory seam</b> (default, in-house broker) — {@link InMemoryKafkaTransport#createPair()};
 *       no sockets, deterministic. The server end is fed to {@link KafkaBroker#handleConnection}
 *       (the same seam the service layer uses); the client end is returned. This is the compliant
 *       reference-demo pattern.</li>
 *   <li><b>Real TCP</b> (external Apache Kafka) — a {@link KafkaClientService} driven by the
 *       {@link SelectableChannelManager}; the open {@link ssg.legoflow.messaging.kafka.transport.
 *       PipelineKafkaTransport} is returned. Used when {@code DemoKafkaAll.USE_EXTERNAL=true}.</li>
 * </ul>
 *
 * <p>Callers open several simultaneously (producer + consumer + admin); each is closed when the
 * client built over it is done.
 */
final class KafkaDemoClient implements AutoCloseable {

    /** Opens a fresh client connection. */
    @FunctionalInterface
    interface Factory {
        KafkaDemoClient open() throws IOException;
    }

    private final KafkaTransport transport;
    private final KafkaClientService clientService; // non-null only in TCP mode
    private final SelectableChannelManager manager; // non-null only in TCP mode
    private final ServiceContext ctx;               // non-null only in TCP mode

    private KafkaDemoClient(KafkaTransport transport, KafkaClientService clientService,
                            SelectableChannelManager manager, ServiceContext ctx) {
        this.transport = transport;
        this.clientService = clientService;
        this.manager = manager;
        this.ctx = ctx;
    }

    /**
     * A factory that opens deterministic in-memory connections to an in-house broker (no sockets).
     *
     * @param broker the running in-house broker; each call wires a fresh pair to it
     */
    static Factory inMemory(KafkaBroker broker) {
        return () -> {
            var pair = InMemoryKafkaTransport.createPair();
            broker.handleConnection(pair[0]);
            return new KafkaDemoClient(pair[1], null, null, null);
        };
    }

    /**
     * A factory that opens real-TCP connections to an external Apache Kafka broker, each driven by
     * the {@link SelectableChannelManager} (the production client path). Each call opens a fresh
     * connection + its own event loop; close the returned {@link KafkaDemoClient} to release both.
     */
    static Factory tcp(String host, int port) {
        return () -> {
            var c = new DefaultServiceContext(ServiceUser.anonymous());
            var m = new SelectableChannelManager(c);
            c.setAttribute("channelManager", m);
            m.startEventLoop();
            var service = KafkaClientService.builder(host, port).build();
            service.connect(c);
            return new KafkaDemoClient(service.getTransport(), service, m, c);
        };
    }

    /** The open transport to construct a client over. */
    KafkaTransport transport() { return transport; }

    @Override
    public void close() {
        try {
            if (transport != null) transport.close();
        } catch (Exception ignored) {}
        if (clientService != null) {
            try {
                clientService.disconnect(ctx);
            } catch (Exception ignored) {}
            try {
                if (manager != null) {
                    manager.stopEventLoop();
                    manager.close();
                }
            } catch (Exception ignored) {}
        }
    }
}
