package ssg.legoflow.messaging.kafka.demo;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.client.ConsumerRecord;
import ssg.legoflow.messaging.kafka.client.KafkaConsumer;
import ssg.legoflow.messaging.kafka.client.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.List;
/**
 * Simple demo: start broker, produce messages, consume them.
 *
 * @since 0.1.0
 */
public final class SimpleProducerConsumerDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleProducerConsumerDemo.class);

    private SimpleProducerConsumerDemo() {
    }

    /**
     * Runs the simple produce/consume demo.
     *
     * @param messageCount the number of messages to produce
     * @return the number of messages consumed
     * @throws IOException if an error occurs
     */
    public static int run(int messageCount) throws IOException {
        try (KafkaBroker broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            broker.createTopic("demo-topic", 3);
            int port = broker.port();

            // Produce
            try (KafkaProducer producer = new KafkaProducer("localhost", port, "demo-producer")) {
                producer.init();
                for (int i = 0; i < messageCount; i++) {
                    var result = producer.send("demo-topic", "key-" + i, "value-" + i);
                    LOG.info("Produced: offset={}, partition={}", result.offset(), result.partition());
                }
            }

            // Consume
            int consumed = 0;
            try (KafkaConsumer consumer = new KafkaConsumer("localhost", port, "demo-consumer", "demo-group")) {
                consumer.subscribe(List.of("demo-topic"));
                List<ConsumerRecord> records = consumer.poll(5000);
                consumed = records.size();
                for (ConsumerRecord rec : records) {
                    LOG.info("Consumed: {}", rec);
                }
                consumer.commitSync();
            }

            LOG.info("Demo complete: produced={}, consumed={}", messageCount, consumed);
            return consumed;
        }
    }
}
