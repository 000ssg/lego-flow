package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SnmpOids} well-known OID constants.
 *
 * @since 1.0.0
 */
class SnmpOidsTest {

    @Test
    void testMib2OidPrefix() {
        assertThat(SnmpOids.MIB_2.toDottedString()).isEqualTo("1.3.6.1.2.1");
    }

    @Test
    void testSystemGroupIsSubtreeOfMib2() {
        assertThat(SnmpOids.SYSTEM.startsWith(SnmpOids.MIB_2)).isTrue();
    }

    @Test
    void testSysDescrIsSubtreeOfSystem() {
        assertThat(SnmpOids.SYS_DESCR.startsWith(SnmpOids.SYSTEM)).isTrue();
    }

    @Test
    void testSysUpTimeOid() {
        assertThat(SnmpOids.SYS_UP_TIME.toDottedString()).isEqualTo("1.3.6.1.2.1.1.3.0");
    }

    @Test
    void testSnmpTrapOid() {
        assertThat(SnmpOids.SNMP_TRAP_OID.toDottedString()).isEqualTo("1.3.6.1.6.3.1.1.4.1.0");
    }

    @Test
    void testColdStartTrapOid() {
        assertThat(SnmpOids.COLD_START.toDottedString()).isEqualTo("1.3.6.1.6.3.1.1.5.1");
    }

    @Test
    void testUsmStatsOids() {
        assertThat(SnmpOids.USM_STATS.toDottedString()).isEqualTo("1.3.6.1.6.3.15.1.1");
        assertThat(SnmpOids.USM_STATS_UNKNOWN_USER_NAMES.startsWith(SnmpOids.USM_STATS)).isTrue();
    }

    @Test
    void testOidComparison() {
        assertThat(SnmpOids.SYS_DESCR).isLessThan(SnmpOids.SYS_UP_TIME);
        assertThat(SnmpOids.SYS_UP_TIME).isGreaterThan(SnmpOids.SYS_DESCR);
    }

    @Test
    void testOidChildCreation() {
        ObjectIdentifier child = SnmpOids.SYSTEM.child(99);
        assertThat(child.toDottedString()).isEqualTo("1.3.6.1.2.1.1.99");
        assertThat(child.startsWith(SnmpOids.SYSTEM)).isTrue();
    }

    @Test
    void testEnterprisesOid() {
        assertThat(SnmpOids.ENTERPRISES.toDottedString()).isEqualTo("1.3.6.1.4.1");
        assertThat(SnmpOids.ENTERPRISES.startsWith(SnmpOids.INTERNET)).isTrue();
    }
}
