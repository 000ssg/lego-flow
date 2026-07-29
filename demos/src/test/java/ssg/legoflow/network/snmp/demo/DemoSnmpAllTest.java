package ssg.legoflow.network.snmp.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive SNMP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code SnmpAgent}. To test against
 * an external Net-SNMP agent, set {@code DemoSnmpAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 *
 * @since 1.0.0
 */
class DemoSnmpAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoSnmpAll.runAll();

        assertThat(results.getOperation())
                .as("GET returns requested OID values")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.getNextOperation())
                .as("GETNEXT walks through MIB entries")
                .isGreaterThanOrEqualTo(5);

        assertThat(results.getBulkOperation())
                .as("GETBULK returns multiple entries")
                .isGreaterThanOrEqualTo(3);

        assertThat(results.setOperation())
                .as("SET updates MIB tree values")
                .isTrue();

        assertThat(results.mibTreeOperations())
                .as("MIB tree subtree, getNext, and contains all succeed")
                .isTrue();

        assertThat(results.messageEncoding())
                .as("SNMPv3 message BER round-trip preserves fields")
                .isTrue();

        assertThat(results.usmSecurity())
                .as("USM authentication digest computation and verification succeed")
                .isTrue();

        assertThat(results.vacmAccessControl())
                .as("VACM access control decisions are correct")
                .isTrue();

        assertThat(results.trapInformPdus())
                .as("Trap and Inform PDU types construct and encode correctly")
                .isTrue();

        assertThat(results.valueTypes())
                .as("All 12 SMIv2 value types created successfully")
                .isEqualTo(12);
    }
}
