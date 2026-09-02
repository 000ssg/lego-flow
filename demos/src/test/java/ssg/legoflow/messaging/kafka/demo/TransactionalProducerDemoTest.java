package ssg.legoflow.messaging.kafka.demo;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
class TransactionalProducerDemoTest {

    @Test
    void testTransactionalDemoCommit() throws IOException {
        try (KafkaBroker broker = TransactionalProducerDemo.run(true)) {
            assertThat(broker.topicNames()).contains("txn-topic");
        }
    }

    @Test
    void testTransactionalDemoAbort() throws IOException {
        try (KafkaBroker broker = TransactionalProducerDemo.run(false)) {
            assertThat(broker.topicNames()).contains("txn-topic");
        }
    }
}
