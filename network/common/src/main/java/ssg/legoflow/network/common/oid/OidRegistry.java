package ssg.legoflow.network.common.oid;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for mapping OIDs to human-readable names and vice versa.
 *
 * <p>Pre-populated with well-known OIDs from X.500, SNMP MIB-2, LDAP,
 * and X.509. Additional OIDs can be registered at runtime.
 *
 * @since 1.0.0
 */
public final class OidRegistry {

    private static final OidRegistry INSTANCE = new OidRegistry();

    private final Map<ObjectIdentifier, String> oidToName = new ConcurrentHashMap<>();
    private final Map<String, ObjectIdentifier> nameToOid = new ConcurrentHashMap<>();

    private OidRegistry() {
        // X.500 attribute types
        register(StandardOids.ID_AT_COMMON_NAME, "id-at-commonName");
        register(StandardOids.ID_AT_SURNAME, "id-at-surname");
        register(StandardOids.ID_AT_COUNTRY_NAME, "id-at-countryName");
        register(StandardOids.ID_AT_LOCALITY_NAME, "id-at-localityName");
        register(StandardOids.ID_AT_STATE_OR_PROVINCE_NAME, "id-at-stateOrProvinceName");
        register(StandardOids.ID_AT_ORGANIZATION_NAME, "id-at-organizationName");
        register(StandardOids.ID_AT_ORGANIZATIONAL_UNIT_NAME, "id-at-organizationalUnitName");

        // SNMP MIB-2
        register(StandardOids.INTERNET, "internet");
        register(StandardOids.MGMT, "mgmt");
        register(StandardOids.MIB_2, "mib-2");
        register(StandardOids.SYSTEM, "system");
        register(StandardOids.SYS_DESCR, "sysDescr");
        register(StandardOids.SYS_OBJECT_ID, "sysObjectID");
        register(StandardOids.SYS_UP_TIME, "sysUpTime");
        register(StandardOids.SYS_CONTACT, "sysContact");
        register(StandardOids.SYS_NAME, "sysName");
        register(StandardOids.SYS_LOCATION, "sysLocation");
        register(StandardOids.INTERFACES, "interfaces");
        register(StandardOids.IF_NUMBER, "ifNumber");
        register(StandardOids.SNMP_MIB, "snmpMIB");
        register(StandardOids.SNMP_FRAMEWORK_MIB, "snmpFrameworkMIB");
        register(StandardOids.PRIVATE, "private");
        register(StandardOids.ENTERPRISES, "enterprises");

        // LDAP
        register(StandardOids.LDAP_PAGED_RESULTS, "pagedResultsControl");
        register(StandardOids.LDAP_START_TLS, "startTLS");

        // X.509
        register(StandardOids.ID_CE, "id-ce");
        register(StandardOids.ID_CE_SUBJECT_ALT_NAME, "id-ce-subjectAltName");
        register(StandardOids.ID_CE_KEY_USAGE, "id-ce-keyUsage");
        register(StandardOids.RSA_ENCRYPTION, "rsaEncryption");
        register(StandardOids.SHA256_WITH_RSA, "sha256WithRSAEncryption");
    }

    /**
     * Returns the global OID registry instance.
     *
     * @return the singleton registry
     */
    public static OidRegistry instance() {
        return INSTANCE;
    }

    /**
     * Registers an OID with a human-readable name.
     *
     * @param oid  the OID
     * @param name the name
     */
    public void register(ObjectIdentifier oid, String name) {
        oidToName.put(oid, name);
        nameToOid.put(name, oid);
    }

    /**
     * Looks up the name for an OID.
     *
     * @param oid the OID to look up
     * @return the name, or empty if not registered
     */
    public Optional<String> nameOf(ObjectIdentifier oid) {
        return Optional.ofNullable(oidToName.get(oid));
    }

    /**
     * Looks up the OID for a name.
     *
     * @param name the name to look up
     * @return the OID, or empty if not registered
     */
    public Optional<ObjectIdentifier> oidOf(String name) {
        return Optional.ofNullable(nameToOid.get(name));
    }

    /**
     * Returns a display string for the OID: name if registered, otherwise dotted notation.
     *
     * @param oid the OID
     * @return a human-readable string
     */
    public String displayName(ObjectIdentifier oid) {
        String name = oidToName.get(oid);
        return name != null ? name + " (" + oid + ")" : oid.toString();
    }

    /**
     * Returns the number of registered OIDs.
     *
     * @return the count
     */
    public int size() {
        return oidToName.size();
    }
}
