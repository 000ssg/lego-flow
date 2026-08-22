package ssg.legoflow.media.common.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive SDP/media-common demo and verifies all feature sections.
 *
 * @since 0.1.0
 */
class DemoSdpAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoSdpAll.runAll();

        assertThat(results.sdpParsing())
                .as("SDP parsing produces valid SessionDescription")
                .isTrue();

        assertThat(results.sdpWriting())
                .as("SDP writing produces RFC 4566 text")
                .isTrue();

        assertThat(results.roundTrip())
                .as("Parse-write-parse round-trip preserves data")
                .isTrue();

        assertThat(results.sessionBuilder())
                .as("SessionBuilder produces valid session")
                .isTrue();

        assertThat(results.mediaBuilder())
                .as("MediaBuilder produces valid media description")
                .isTrue();

        assertThat(results.negotiation())
                .as("Offer/answer negotiation produces valid answer")
                .isTrue();

        assertThat(results.payloadRegistry())
                .as("Payload registry has static types")
                .isGreaterThanOrEqualTo(18);

        assertThat(results.modelTypes())
                .as("SDP model types parse and format correctly")
                .isTrue();
    }
}
