package ssg.legoflow.network.snmp.protocol;

import ssg.legoflow.network.common.oid.ObjectIdentifier;

/**
 * Well-known SNMP OIDs from standard MIBs.
 *
 * <p>Provides constants for commonly referenced OIDs in SNMP operations,
 * including MIB-2 system group, interface group, trap OIDs, and USM OIDs.
 *
 * @since 0.1.0
 */
public final class SnmpOids {

    private SnmpOids() {}

    // ── MIB-2 root ──

    /** iso.org.dod.internet (1.3.6.1). */
    public static final ObjectIdentifier INTERNET = ObjectIdentifier.parse("1.3.6.1");

    /** iso.org.dod.internet.mgmt (1.3.6.1.2). */
    public static final ObjectIdentifier MGMT = ObjectIdentifier.parse("1.3.6.1.2");

    /** iso.org.dod.internet.mgmt.mib-2 (1.3.6.1.2.1). */
    public static final ObjectIdentifier MIB_2 = ObjectIdentifier.parse("1.3.6.1.2.1");

    /** iso.org.dod.internet.private.enterprises (1.3.6.1.4.1). */
    public static final ObjectIdentifier ENTERPRISES = ObjectIdentifier.parse("1.3.6.1.4.1");

    // ── System group (1.3.6.1.2.1.1) ──

    /** system (1.3.6.1.2.1.1). */
    public static final ObjectIdentifier SYSTEM = ObjectIdentifier.parse("1.3.6.1.2.1.1");

    /** sysDescr.0 (1.3.6.1.2.1.1.1.0). */
    public static final ObjectIdentifier SYS_DESCR = ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0");

    /** sysObjectID.0 (1.3.6.1.2.1.1.2.0). */
    public static final ObjectIdentifier SYS_OBJECT_ID = ObjectIdentifier.parse("1.3.6.1.2.1.1.2.0");

    /** sysUpTime.0 (1.3.6.1.2.1.1.3.0). */
    public static final ObjectIdentifier SYS_UP_TIME = ObjectIdentifier.parse("1.3.6.1.2.1.1.3.0");

    /** sysContact.0 (1.3.6.1.2.1.1.4.0). */
    public static final ObjectIdentifier SYS_CONTACT = ObjectIdentifier.parse("1.3.6.1.2.1.1.4.0");

    /** sysName.0 (1.3.6.1.2.1.1.5.0). */
    public static final ObjectIdentifier SYS_NAME = ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0");

    /** sysLocation.0 (1.3.6.1.2.1.1.6.0). */
    public static final ObjectIdentifier SYS_LOCATION = ObjectIdentifier.parse("1.3.6.1.2.1.1.6.0");

    /** sysServices.0 (1.3.6.1.2.1.1.7.0). */
    public static final ObjectIdentifier SYS_SERVICES = ObjectIdentifier.parse("1.3.6.1.2.1.1.7.0");

    // ── Interface group (1.3.6.1.2.1.2) ──

    /** interfaces (1.3.6.1.2.1.2). */
    public static final ObjectIdentifier INTERFACES = ObjectIdentifier.parse("1.3.6.1.2.1.2");

    /** ifNumber.0 (1.3.6.1.2.1.2.1.0). */
    public static final ObjectIdentifier IF_NUMBER = ObjectIdentifier.parse("1.3.6.1.2.1.2.1.0");

    // ── SNMP group (1.3.6.1.2.1.11) ──

    /** snmp (1.3.6.1.2.1.11). */
    public static final ObjectIdentifier SNMP = ObjectIdentifier.parse("1.3.6.1.2.1.11");

    // ── Trap OIDs ──

    /** snmpTrapOID.0 (1.3.6.1.6.3.1.1.4.1.0). */
    public static final ObjectIdentifier SNMP_TRAP_OID = ObjectIdentifier.parse("1.3.6.1.6.3.1.1.4.1.0");

    /** coldStart trap (1.3.6.1.6.3.1.1.5.1). */
    public static final ObjectIdentifier COLD_START = ObjectIdentifier.parse("1.3.6.1.6.3.1.1.5.1");

    /** warmStart trap (1.3.6.1.6.3.1.1.5.2). */
    public static final ObjectIdentifier WARM_START = ObjectIdentifier.parse("1.3.6.1.6.3.1.1.5.2");

    /** linkDown trap (1.3.6.1.6.3.1.1.5.3). */
    public static final ObjectIdentifier LINK_DOWN = ObjectIdentifier.parse("1.3.6.1.6.3.1.1.5.3");

    /** linkUp trap (1.3.6.1.6.3.1.1.5.4). */
    public static final ObjectIdentifier LINK_UP = ObjectIdentifier.parse("1.3.6.1.6.3.1.1.5.4");

    /** authenticationFailure trap (1.3.6.1.6.3.1.1.5.5). */
    public static final ObjectIdentifier AUTH_FAILURE = ObjectIdentifier.parse("1.3.6.1.6.3.1.1.5.5");

    // ── USM OIDs (1.3.6.1.6.3.15) ──

    /** usmStats (1.3.6.1.6.3.15.1.1). */
    public static final ObjectIdentifier USM_STATS = ObjectIdentifier.parse("1.3.6.1.6.3.15.1.1");

    /** usmStatsUnsupportedSecLevels (1.3.6.1.6.3.15.1.1.1.0). */
    public static final ObjectIdentifier USM_STATS_UNSUPPORTED_SEC_LEVELS =
            ObjectIdentifier.parse("1.3.6.1.6.3.15.1.1.1.0");

    /** usmStatsNotInTimeWindows (1.3.6.1.6.3.15.1.1.2.0). */
    public static final ObjectIdentifier USM_STATS_NOT_IN_TIME_WINDOWS =
            ObjectIdentifier.parse("1.3.6.1.6.3.15.1.1.2.0");

    /** usmStatsUnknownUserNames (1.3.6.1.6.3.15.1.1.3.0). */
    public static final ObjectIdentifier USM_STATS_UNKNOWN_USER_NAMES =
            ObjectIdentifier.parse("1.3.6.1.6.3.15.1.1.3.0");

    /** usmStatsUnknownEngineIDs (1.3.6.1.6.3.15.1.1.4.0). */
    public static final ObjectIdentifier USM_STATS_UNKNOWN_ENGINE_IDS =
            ObjectIdentifier.parse("1.3.6.1.6.3.15.1.1.4.0");

    /** usmStatsWrongDigests (1.3.6.1.6.3.15.1.1.5.0). */
    public static final ObjectIdentifier USM_STATS_WRONG_DIGESTS =
            ObjectIdentifier.parse("1.3.6.1.6.3.15.1.1.5.0");

    /** usmStatsDecryptionErrors (1.3.6.1.6.3.15.1.1.6.0). */
    public static final ObjectIdentifier USM_STATS_DECRYPTION_ERRORS =
            ObjectIdentifier.parse("1.3.6.1.6.3.15.1.1.6.0");
}
