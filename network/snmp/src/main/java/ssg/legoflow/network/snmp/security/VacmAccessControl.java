package ssg.legoflow.network.snmp.security;

import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.protocol.SecurityLevel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * View-based Access Control Model (VACM) as defined in RFC 3415.
 *
 * <p>Implements the three VACM tables:
 * <ul>
 *   <li>Security-to-group mapping (vacmSecurityToGroupTable)</li>
 *   <li>Access table (vacmAccessTable)</li>
 *   <li>View tree family table (vacmViewTreeFamilyTable)</li>
 * </ul>
 *
 * <p>This class is thread-safe.
 *
 * @since 1.0.0
 */
public final class VacmAccessControl {

    /** Access types for VACM lookups. */
    public enum AccessType {
        /** Read access (GET, GETNEXT, GETBULK). */
        READ,
        /** Write access (SET). */
        WRITE,
        /** Notify access (TRAP, INFORM). */
        NOTIFY
    }

    private final ConcurrentHashMap<SecurityGroupKey, String> securityToGroup = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AccessKey, AccessEntry> accessTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ViewEntry>> viewTable = new ConcurrentHashMap<>();

    /**
     * Adds a security-to-group mapping.
     *
     * @param securityModel the security model (3 for USM)
     * @param securityName  the security name (user name)
     * @param groupName     the group name
     */
    public void addSecurityToGroup(int securityModel, String securityName, String groupName) {
        securityToGroup.put(new SecurityGroupKey(securityModel, securityName), groupName);
    }

    /**
     * Looks up the group for a security name.
     *
     * @param securityModel the security model
     * @param securityName  the security name
     * @return the group name, or null if not found
     */
    public String getGroup(int securityModel, String securityName) {
        return securityToGroup.get(new SecurityGroupKey(securityModel, securityName));
    }

    /**
     * Adds an access entry.
     *
     * @param groupName      the group name
     * @param contextPrefix  the context prefix (empty for default)
     * @param securityModel  the security model
     * @param securityLevel  the minimum security level
     * @param readViewName   the read view name (empty for no access)
     * @param writeViewName  the write view name (empty for no access)
     * @param notifyViewName the notify view name (empty for no access)
     */
    public void addAccess(String groupName, String contextPrefix, int securityModel,
                          SecurityLevel securityLevel, String readViewName,
                          String writeViewName, String notifyViewName) {
        AccessKey key = new AccessKey(groupName, contextPrefix, securityModel, securityLevel);
        accessTable.put(key, new AccessEntry(readViewName, writeViewName, notifyViewName));
    }

    /**
     * Looks up the view name for the given access parameters and type.
     *
     * @param groupName     the group name
     * @param contextName   the context name
     * @param securityModel the security model
     * @param securityLevel the security level
     * @param accessType    the access type
     * @return the view name, or null if no matching entry
     */
    public String getViewName(String groupName, String contextName, int securityModel,
                               SecurityLevel securityLevel, AccessType accessType) {
        // Try exact match first, then empty context prefix
        AccessEntry entry = accessTable.get(
                new AccessKey(groupName, contextName, securityModel, securityLevel));
        if (entry == null) {
            entry = accessTable.get(
                    new AccessKey(groupName, "", securityModel, securityLevel));
        }
        if (entry == null) return null;

        return switch (accessType) {
            case READ -> entry.readViewName();
            case WRITE -> entry.writeViewName();
            case NOTIFY -> entry.notifyViewName();
        };
    }

    /**
     * Adds a view tree family entry (subtree inclusion or exclusion).
     *
     * @param viewName the view name
     * @param subtree  the OID subtree
     * @param mask     the subtree mask (null for full match)
     * @param type     true for included, false for excluded
     */
    public void addView(String viewName, ObjectIdentifier subtree, byte[] mask, boolean type) {
        viewTable.computeIfAbsent(viewName, _ -> new CopyOnWriteArrayList<>())
                .add(new ViewEntry(subtree, mask != null ? mask.clone() : null, type));
    }

    /**
     * Checks whether the given OID is in the specified view.
     *
     * @param viewName the view name
     * @param oid      the OID to check
     * @return true if the OID is included in the view
     */
    public boolean isInView(String viewName, ObjectIdentifier oid) {
        List<ViewEntry> entries = viewTable.get(viewName);
        if (entries == null || entries.isEmpty()) {
            return false;
        }

        boolean included = false;
        for (ViewEntry entry : entries) {
            if (matchesSubtree(oid, entry.subtree(), entry.mask())) {
                if (entry.included()) {
                    included = true;
                } else {
                    included = false;
                }
            }
        }
        return included;
    }

    /**
     * Checks whether access is allowed for the given security context and OID.
     *
     * @param securityModel the security model
     * @param securityName  the security name
     * @param securityLevel the security level
     * @param contextName   the context name
     * @param accessType    the access type
     * @param oid           the OID to check
     * @return true if access is permitted
     */
    public boolean isAccessAllowed(int securityModel, String securityName,
                                    SecurityLevel securityLevel, String contextName,
                                    AccessType accessType, ObjectIdentifier oid) {
        String groupName = getGroup(securityModel, securityName);
        if (groupName == null) return false;

        String viewName = getViewName(groupName, contextName, securityModel,
                securityLevel, accessType);
        if (viewName == null || viewName.isEmpty()) return false;

        return isInView(viewName, oid);
    }

    private boolean matchesSubtree(ObjectIdentifier oid, ObjectIdentifier subtree, byte[] mask) {
        if (mask == null) {
            // No mask: exact prefix match
            return oid.startsWith(subtree);
        }

        int[] oidArcs = oid.arcs();
        int[] subtreeArcs = subtree.arcs();

        if (oidArcs.length < subtreeArcs.length) {
            return false;
        }

        for (int i = 0; i < subtreeArcs.length; i++) {
            // Check if this arc position is masked
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8);
            if (byteIndex < mask.length && ((mask[byteIndex] >> bitIndex) & 1) == 1) {
                // This arc must match
                if (oidArcs[i] != subtreeArcs[i]) {
                    return false;
                }
            }
        }
        return true;
    }

    // ── Internal records ──

    private record SecurityGroupKey(int securityModel, String securityName) {}

    private record AccessKey(String groupName, String contextPrefix,
                             int securityModel, SecurityLevel securityLevel) {}

    private record AccessEntry(String readViewName, String writeViewName, String notifyViewName) {}

    private record ViewEntry(ObjectIdentifier subtree, byte[] mask, boolean included) {
        ViewEntry {
            if (mask != null) {
                mask = mask.clone();
            }
        }

        @Override
        public byte[] mask() {
            return mask != null ? mask.clone() : null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ViewEntry other
                    && subtree.equals(other.subtree)
                    && Arrays.equals(mask, other.mask)
                    && included == other.included;
        }

        @Override
        public int hashCode() {
            return 31 * subtree.hashCode() + Arrays.hashCode(mask) + Boolean.hashCode(included);
        }
    }
}
