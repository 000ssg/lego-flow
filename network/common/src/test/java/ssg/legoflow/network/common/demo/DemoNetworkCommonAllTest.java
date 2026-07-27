package ssg.legoflow.network.common.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive network-common (BER/ASN.1) demo and verifies all feature sections.
 *
 * @since 1.0.0
 */
class DemoNetworkCommonAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoNetworkCommonAll.runAll();

        assertThat(results.primitiveRoundTrip())
                .as("All 5 primitive types round-trip correctly")
                .isEqualTo(5);

        assertThat(results.stringRoundTrip())
                .as("All 4 string types round-trip correctly")
                .isEqualTo(4);

        assertThat(results.bitStringRoundTrip())
                .as("BitString preserves data and unused bits")
                .isTrue();

        assertThat(results.oidRoundTrip())
                .as("OID encoding/decoding preserves all arcs")
                .isTrue();

        assertThat(results.sequenceRoundTrip())
                .as("Sequence contains all 4 elements after round-trip")
                .isEqualTo(4);

        assertThat(results.setRoundTrip())
                .as("Set contains all 3 elements after round-trip")
                .isEqualTo(3);

        assertThat(results.contextSpecificTags())
                .as("Both explicit and implicit context-specific tags work")
                .isTrue();

        assertThat(results.derCanonicalEncoding())
                .as("DER sorts SET elements by tag value")
                .isTrue();

        assertThat(results.oidOperations())
                .as("OID parsing, prefix, child, and comparison all succeed")
                .isTrue();

        assertThat(results.oidRegistryLookups())
                .as("OID registry lookups return expected results")
                .isGreaterThanOrEqualTo(4);
    }
}
