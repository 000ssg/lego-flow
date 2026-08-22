package ssg.legoflow.coap.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link CoapClientConfig}.
 *
 * @since 0.1.0
 */
class CoapClientConfigTest {

    @Test
    void testDefaults() {
        var config = CoapClientConfig.defaults("localhost");

        assertThat(config.host()).isEqualTo("localhost");
        assertThat(config.port()).isEqualTo(5683);
        assertThat(config.ackTimeout()).isEqualTo(2000);
        assertThat(config.maxRetransmit()).isEqualTo(4);
        assertThat(config.preferredBlockSize()).isEqualTo(512);
    }

    @Test
    void testDefaultsWithPort() {
        var config = CoapClientConfig.defaults("localhost", 8683);

        assertThat(config.host()).isEqualTo("localhost");
        assertThat(config.port()).isEqualTo(8683);
    }

    @Test
    void testNullHostThrows() {
        assertThatThrownBy(() -> CoapClientConfig.defaults(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInvalidPortThrows() {
        assertThatThrownBy(() -> new CoapClientConfig("host", 0, 2000, 4, 512))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
