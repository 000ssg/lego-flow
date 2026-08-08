package ssg.legoflow.network.common.oid;

/**
 * Common well-known OIDs used by LDAP, SNMP, X.509, and other protocols.
 *
 * @since 0.1.0
 */
public final class StandardOids {

    private StandardOids() {}

    // ── X.500 attribute types (2.5.4.*) ──

    /** id-at-commonName (2.5.4.3). */
    public static final ObjectIdentifier ID_AT_COMMON_NAME = ObjectIdentifier.parse("2.5.4.3");
    /** id-at-surname (2.5.4.4). */
    public static final ObjectIdentifier ID_AT_SURNAME = ObjectIdentifier.parse("2.5.4.4");
    /** id-at-countryName (2.5.4.6). */
    public static final ObjectIdentifier ID_AT_COUNTRY_NAME = ObjectIdentifier.parse("2.5.4.6");
    /** id-at-localityName (2.5.4.7). */
    public static final ObjectIdentifier ID_AT_LOCALITY_NAME = ObjectIdentifier.parse("2.5.4.7");
    /** id-at-stateOrProvinceName (2.5.4.8). */
    public static final ObjectIdentifier ID_AT_STATE_OR_PROVINCE_NAME = ObjectIdentifier.parse("2.5.4.8");
    /** id-at-organizationName (2.5.4.10). */
    public static final ObjectIdentifier ID_AT_ORGANIZATION_NAME = ObjectIdentifier.parse("2.5.4.10");
    /** id-at-organizationalUnitName (2.5.4.11). */
    public static final ObjectIdentifier ID_AT_ORGANIZATIONAL_UNIT_NAME = ObjectIdentifier.parse("2.5.4.11");

    // ── SNMP MIB-2 (1.3.6.1.2.1.*) ──

    /** iso.org.dod.internet (1.3.6.1). */
    public static final ObjectIdentifier INTERNET = ObjectIdentifier.parse("1.3.6.1");
    /** iso.org.dod.internet.mgmt (1.3.6.1.2). */
    public static final ObjectIdentifier MGMT = ObjectIdentifier.parse("1.3.6.1.2");
    /** iso.org.dod.internet.mgmt.mib-2 (1.3.6.1.2.1). */
    public static final ObjectIdentifier MIB_2 = ObjectIdentifier.parse("1.3.6.1.2.1");
    /** mib-2.system (1.3.6.1.2.1.1). */
    public static final ObjectIdentifier SYSTEM = ObjectIdentifier.parse("1.3.6.1.2.1.1");
    /** sysDescr (1.3.6.1.2.1.1.1). */
    public static final ObjectIdentifier SYS_DESCR = ObjectIdentifier.parse("1.3.6.1.2.1.1.1");
    /** sysObjectID (1.3.6.1.2.1.1.2). */
    public static final ObjectIdentifier SYS_OBJECT_ID = ObjectIdentifier.parse("1.3.6.1.2.1.1.2");
    /** sysUpTime (1.3.6.1.2.1.1.3). */
    public static final ObjectIdentifier SYS_UP_TIME = ObjectIdentifier.parse("1.3.6.1.2.1.1.3");
    /** sysContact (1.3.6.1.2.1.1.4). */
    public static final ObjectIdentifier SYS_CONTACT = ObjectIdentifier.parse("1.3.6.1.2.1.1.4");
    /** sysName (1.3.6.1.2.1.1.5). */
    public static final ObjectIdentifier SYS_NAME = ObjectIdentifier.parse("1.3.6.1.2.1.1.5");
    /** sysLocation (1.3.6.1.2.1.1.6). */
    public static final ObjectIdentifier SYS_LOCATION = ObjectIdentifier.parse("1.3.6.1.2.1.1.6");
    /** mib-2.interfaces (1.3.6.1.2.1.2). */
    public static final ObjectIdentifier INTERFACES = ObjectIdentifier.parse("1.3.6.1.2.1.2");
    /** ifNumber (1.3.6.1.2.1.2.1). */
    public static final ObjectIdentifier IF_NUMBER = ObjectIdentifier.parse("1.3.6.1.2.1.2.1");

    // ── SNMP framework (1.3.6.1.6.3.*) ──

    /** snmpMIB (1.3.6.1.6.3.1). */
    public static final ObjectIdentifier SNMP_MIB = ObjectIdentifier.parse("1.3.6.1.6.3.1");
    /** snmpFrameworkMIB (1.3.6.1.6.3.10). */
    public static final ObjectIdentifier SNMP_FRAMEWORK_MIB = ObjectIdentifier.parse("1.3.6.1.6.3.10");

    // ── SNMP private enterprises (1.3.6.1.4.1) ──

    /** iso.org.dod.internet.private (1.3.6.1.4). */
    public static final ObjectIdentifier PRIVATE = ObjectIdentifier.parse("1.3.6.1.4");
    /** iso.org.dod.internet.private.enterprises (1.3.6.1.4.1). */
    public static final ObjectIdentifier ENTERPRISES = ObjectIdentifier.parse("1.3.6.1.4.1");

    // ── LDAP controls and extended operations ──

    /** LDAP PagedResultsControl (1.2.840.113556.1.4.319). */
    public static final ObjectIdentifier LDAP_PAGED_RESULTS = ObjectIdentifier.parse("1.2.840.113556.1.4.319");
    /** LDAP StartTLS extended operation (1.3.6.1.4.1.1466.20037). */
    public static final ObjectIdentifier LDAP_START_TLS = ObjectIdentifier.parse("1.3.6.1.4.1.1466.20037");

    // ── X.509 / PKIX ──

    /** id-ce (2.5.29) — certificate extensions. */
    public static final ObjectIdentifier ID_CE = ObjectIdentifier.parse("2.5.29");
    /** id-ce-subjectAltName (2.5.29.17). */
    public static final ObjectIdentifier ID_CE_SUBJECT_ALT_NAME = ObjectIdentifier.parse("2.5.29.17");
    /** id-ce-keyUsage (2.5.29.15). */
    public static final ObjectIdentifier ID_CE_KEY_USAGE = ObjectIdentifier.parse("2.5.29.15");

    // ── Algorithms ──

    /** rsaEncryption (1.2.840.113549.1.1.1). */
    public static final ObjectIdentifier RSA_ENCRYPTION = ObjectIdentifier.parse("1.2.840.113549.1.1.1");
    /** sha256WithRSAEncryption (1.2.840.113549.1.1.11). */
    public static final ObjectIdentifier SHA256_WITH_RSA = ObjectIdentifier.parse("1.2.840.113549.1.1.11");
}
