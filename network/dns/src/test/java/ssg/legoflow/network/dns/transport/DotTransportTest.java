package ssg.legoflow.network.dns.transport;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import javax.net.ssl.SSLContext;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for DotTransport covering constructor validation and basic behavior.
 */
@Timeout(10)
class DotTransportTest {

    @Test
    void testDefaultConstructor() throws Exception {
        DotTransport transport = new DotTransport(Duration.ofSeconds(5));
        try {
            assertThat(transport).isNotNull();
        } finally {
            transport.close();
        }
    }

    @Test
    void testDefaultPortConstant() {
        assertThat(DotTransport.DEFAULT_PORT).isEqualTo(853);
    }

    @Test
    void testCustomSslContextConstructor() throws Exception {
        SSLContext sslCtx = SSLContext.getDefault();
        DotTransport transport = new DotTransport(Duration.ofSeconds(5), sslCtx);
        try {
            assertThat(transport).isNotNull();
        } finally {
            transport.close();
        }
    }

    @Test
    void testCloseDoesNotThrow() throws Exception {
        DotTransport transport = new DotTransport(Duration.ofSeconds(1));
        assertThatCode(transport::close).doesNotThrowAnyException();
    }

    @Test
    void testDoubleCloseDoesNotThrow() throws Exception {
        DotTransport transport = new DotTransport(Duration.ofSeconds(1));
        transport.close();
        transport.close(); // Idempotent
    }

    @Test
    void testAutoCloseableCompliance() throws Exception {
        try (DotTransport transport = new DotTransport(Duration.ofSeconds(1))) {
            assertThat(transport).isNotNull();
        }
    }

    @Test
    void testConstructorWithShortTimeout() throws Exception {
        DotTransport transport = new DotTransport(Duration.ofMillis(100));
        try {
            assertThat(transport).isNotNull();
        } finally {
            transport.close();
        }
    }

    @Test
    void testSslContextInitialization() throws Exception {
        SSLContext ctx = SSLContext.getDefault();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getSocketFactory()).isNotNull();
    }
}
