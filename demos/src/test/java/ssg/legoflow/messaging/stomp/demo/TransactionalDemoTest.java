package ssg.legoflow.messaging.stomp.demo;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link TransactionalDemo}.
 *
 * @since 0.1.0
 */
class TransactionalDemoTest {

    @Test
    void testTransactionCommit() throws Exception {
        var demo = new TransactionalDemo();
        try {
            var received = demo.runCommit("/topic/tx-demo",
                    List.of("msg1", "msg2", "msg3"));
            assertThat(received).hasSize(3);
            assertThat(received.get(0).bodyAsText()).isEqualTo("msg1");
            assertThat(received.get(1).bodyAsText()).isEqualTo("msg2");
            assertThat(received.get(2).bodyAsText()).isEqualTo("msg3");
        } finally {
            demo.close();
        }
    }

    @Test
    void testTransactionAbort() throws Exception {
        var demo = new TransactionalDemo();
        try {
            var received = demo.runAbort("/topic/tx-abort-demo",
                    List.of("lost1", "lost2"));
            assertThat(received).isEmpty();
        } finally {
            demo.close();
        }
    }

    @Test
    void testTransactionCommitSingleMessage() throws Exception {
        var demo = new TransactionalDemo();
        try {
            var received = demo.runCommit("/queue/single-tx", List.of("only-one"));
            assertThat(received).hasSize(1);
        } finally {
            demo.close();
        }
    }
}
