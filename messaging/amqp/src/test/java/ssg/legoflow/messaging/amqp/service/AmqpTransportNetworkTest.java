package ssg.legoflow.messaging.amqp.service;
import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import org.junit.jupiter.api.*;
import org.slf4j.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transport-level network I/O test: verifies raw TCP to RabbitMQ works
 * before testing protocol-layer interop. Uses plain sockets since RabbitMQ
 * is an external server — the no-socket rule only applies to lego-flow impls.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AmqpTransportNetworkTest {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpTransportNetworkTest.class);
    private static final String HOST = System.getProperty("interop.amqp.host", "localhost");
    private static final int PORT = Integer.parseInt(System.getProperty("interop.amqp.port", "5672"));

    @Test void testTcpConnectToRabbitMQ() throws Exception {
        try (var s = new Socket(HOST, PORT)) {
            assertThat(s.isConnected()).as("TCP to RabbitMQ").isTrue();
        }
        LOG.info("TCP connect OK");
    }

    @Test void testSendReceiveAMQPHeader() throws Exception {
        try (var s = new Socket(HOST, PORT)) {
            s.getOutputStream().write(AmqpConstants.AMQP_HEADER);
            s.getOutputStream().flush();
            var buf = new byte[8];
            var deadline = System.currentTimeMillis() + 5000;
            int off = 0;
            while (off < 8 && System.currentTimeMillis() < deadline) {
                int r = s.getInputStream().read(buf, off, 8 - off);
                if (r < 0) break;
                off += r;
            }
            assertThat(off).isEqualTo(8);
            // RabbitMQ AMQP 1.0 responds with SASL_HEADER to AMQP_HEADER (wants SASL-first)
            assertThat(buf).isEqualTo(AmqpConstants.SASL_HEADER);
        }
        LOG.info("AMQP_HEADER echo OK");
    }

    @Test void testSendReceiveSASLHeader() throws Exception {
        try (var s = new Socket(HOST, PORT)) {
            s.getOutputStream().write(AmqpConstants.SASL_HEADER);
            s.getOutputStream().flush();
            var buf = new byte[8];
            var deadline = System.currentTimeMillis() + 5000;
            int off = 0;
            while (off < 8 && System.currentTimeMillis() < deadline) {
                int r = s.getInputStream().read(buf, off, 8 - off);
                if (r < 0) break;
                off += r;
            }
            // RabbitMQ AMQP 1.0 echoes SASL_HEADER back (supports SASL-first)
            assertThat(off).isEqualTo(8);
            assertThat(buf).isEqualTo(AmqpConstants.SASL_HEADER);
        }
        LOG.info("SASL_HEADER echo OK");
    }
}
