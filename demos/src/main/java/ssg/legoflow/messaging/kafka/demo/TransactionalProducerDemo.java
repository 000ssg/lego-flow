package ssg.legoflow.messaging.kafka.demo;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.client.KafkaProducer;
import ssg.legoflow.messaging.kafka.common.Partitioner;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import ssg.legoflow.messaging.kafka.record.Compression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.List;
/**
 * Demo: transactional producer with commit/abort.
 *
 * @since 0.1.0
 */
public final class TransactionalProducerDemo {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionalProducerDemo.class);

    private TransactionalProducerDemo() {
    }

    /**
     * Runs the transactional producer demo.
     *
     * @param commitTransaction whether to commit (true) or abort (false)
     * @return the broker for further inspection
     * @throws IOException if an error occurs
     */
    public static KafkaBroker run(boolean commitTransaction) throws IOException {
        KafkaBroker broker = new KafkaBroker("localhost", 0);
        broker.start();
        broker.createTopic("txn-topic", 2);
        int port = broker.port();

        try (KafkaProducer producer = new KafkaProducer("localhost", port, "txn-producer",
                Partitioner.roundRobin(), (short) -1, 3, 100, Compression.NONE, true, "my-txn")) {
            producer.init();
            producer.beginTransaction();
            producer.addPartitionsToTransaction(List.of(
                    new TopicPartition("txn-topic", 0),
                    new TopicPartition("txn-topic", 1)));

            producer.send("txn-topic", "txn-key-1", "txn-value-1");
            producer.send("txn-topic", "txn-key-2", "txn-value-2");

            if (commitTransaction) {
                producer.commitTransaction();
                LOG.info("Transaction committed");
            } else {
                producer.abortTransaction();
                LOG.info("Transaction aborted");
            }
        }

        return broker;
    }
}
