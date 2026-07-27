package ssg.legoflow.messaging.nats.demo;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.server.NatsServer;
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
 * @since 1.0.0
 */
public final class QueueGroupDemo {

    private static final Logger LOG = LoggerFactory.getLogger(QueueGroupDemo.class);

    private QueueGroupDemo() {}

    /**
     * Runs the queue group demo.
     *
     * @param port          the server port (0 for ephemeral)
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

        try (var server = new NatsServer(port)) {
            server.start(port);
            int actualPort = server.port();

            var workers = new NatsClient[numWorkers];
            try {
                // Create workers in queue group
                for (int i = 0; i < numWorkers; i++) {
                    String workerName = "worker-" + i;
                    workers[i] = new NatsClient("localhost", actualPort,
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

                // Publish tasks
                try (var publisher = new NatsClient("localhost", actualPort,
                        ConnectOptions.withDefaults("publisher"))) {
                    publisher.connect();
                    for (int i = 0; i < numMessages; i++) {
                        publisher.publish("tasks", "task-" + i);
                    }
                }

                latch.await(5, TimeUnit.SECONDS);

            } finally {
                for (var worker : workers) {
                    if (worker != null) worker.close();
                }
            }
        }

        return workerCounts;
    }
}
