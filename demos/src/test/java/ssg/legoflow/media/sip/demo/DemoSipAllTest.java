package ssg.legoflow.media.sip.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive SIP demo and verifies all feature sections.
 *
 * @since 0.1.0
 */
class DemoSipAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoSipAll.runAll();

        assertThat(results.sipCodec())
                .as("SIP encode/decode round-trip")
                .isTrue();

        assertThat(results.requestBuilder())
                .as("SIP request builder")
                .isTrue();

        assertThat(results.responseBuilder())
                .as("SIP response builder")
                .isTrue();

        assertThat(results.uriParsing())
                .as("SIP URI parsing")
                .isTrue();

        assertThat(results.registration())
                .as("SIP registrar binding management")
                .isTrue();

        assertThat(results.registrationClient())
                .as("SIP registration client")
                .isTrue();

        assertThat(results.clientTransaction())
                .as("SIP client transaction state machine")
                .isTrue();

        assertThat(results.serverTransaction())
                .as("SIP server transaction state machine")
                .isTrue();

        assertThat(results.dialogManagement())
                .as("SIP dialog lifecycle management")
                .isTrue();
    }
}
