package ssg.legoflow.messaging.nats.demo;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.server.NatsServer;
import ssg.legoflow.messaging.nats.transport.InMemoryNatsTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Demonstrates NATS queue group load balancing.
 *
 * <p>Multiple subscribers in the same queue group receive messages
 * in a round-robin fashion, enabling horizontal scaling.
 *
 * @since 0.1.0
 */
public final class QueueGroupDemo {

    private static final Logger LOG = LoggerFactory.getLogger(QueueGroupDemo.class);

    private QueueGroupDemo() {}

    /**
     * Runs the queue group demo.
     *
     * @param port          the server port (ignored — the demo now runs over an in-memory transport)
     * @param numWorkers    the number of queue group workers
     * @param numMessages   the number of messages to publish
     * @return map of worker name to message count
     * @throws IOException if connection fails
     * @throws InterruptedException if interrupted
     */
    public static ConcurrentHashMap<String, AtomicInteger> run(int port, int numWorkers, int numMessages)
            throws IOException, InterruptedException {

        var workerCounts = new ConcurrentHashMap<String, AtomicInteger>();
        var latch = new CountDownLatch(numMessages);

        try (var server = new NatsServer()) {
            server.start();

            var workers = new NatsClient[numWorkers];
            try {
                // Create workers in queue group; each worker client gets its OWN in-memory pair
                for (int i = 0; i < numWorkers; i++) {
                    String workerName = "worker-" + i;
                    var workerPair = InMemoryNatsTransport.createPair();
                    server.handleConnection(workerPair[0]);
                    workers[i] = new NatsClient(workerPair[1],
                            ConnectOptions.withDefaults(workerName));
                    workers[i].connect();

                    workerCounts.put(workerName, new AtomicInteger(0));
                    final String name = workerName;
                    workers[i].subscribe("tasks", "worker-group", msg -> {
                        workerCounts.get(name).incrementAndGet();
                        LOG.debug("{} processed: {}", name, msg.dataAsString());
                        latch.countDown();
                    });
                }

                Thread.sleep(50);

                // Publish tasks (publisher gets its own in-memory pair)
                var pubPair = InMemoryNatsTransport.createPair();
                server.handleConnection(pubPair[0]);
                var publisher = new NatsClient(pubPair[1],
                        ConnectOptions.withDefaults("publisher"));
                publisher.connect();
                try {
                    for (int i = 0; i < numMessages; i++) {
                        publisher.publish("tasks", "task-" + i);
                    }

                    // Wait for delivery before closing the publisher: closing the
                    // pair early would cut the server-side reader off mid-stream
                    // and drop in-flight messages.
                    latch.await(5, TimeUnit.SECONDS);
                } finally {
                    publisher.close();
                }

            } finally {
                for (var worker : workers) {
                    if (worker != null) worker.close();
                }
            }
        }

        return workerCounts;
    }
}
