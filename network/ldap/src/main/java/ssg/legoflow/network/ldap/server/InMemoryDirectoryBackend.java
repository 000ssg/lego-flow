package ssg.legoflow.network.ldap.server;

import ssg.legoflow.network.ldap.dn.DistinguishedName;
import ssg.legoflow.network.ldap.filter.SearchFilter;
import ssg.legoflow.network.ldap.protocol.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory directory backend for testing purposes.
 *
 * <p>Stores entries in a thread-safe map keyed by normalized DN.
 * Supports bind (simple), search, compare, add, delete, and modify operations.
 *
 * <p>This backend is thread-safe.
 *
 * @since 1.0.0
 */
public final class InMemoryDirectoryBackend implements DirectoryBackend {

    private final ConcurrentHashMap<String, DirectoryEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    /**
     * Adds an entry to the directory.
     *
     * @param dn         the entry DN
     * @param attributes the entry's attributes
     */
    public void addEntry(String dn, List<LdapAttribute> attributes) {
        entries.put(normalizeDn(dn), new DirectoryEntry(dn, new ArrayList<>(attributes)));
    }

    /**
     * Sets credentials for a DN (for simple bind authentication).
     *
     * @param dn       the DN
     * @param password the password
     */
    public void setCredentials(String dn, String password) {
        credentials.put(normalizeDn(dn), password);
    }

    /**
     * Returns the number of entries in the directory.
     *
     * @return the entry count
     */
    public int size() {
        return entries.size();
    }

    @Override
    public LdapResult bind(BindRequest request) {
        if (request.authentication() instanceof BindRequest.AuthenticationChoice.Simple simple) {
            if (request.name().isEmpty() && simple.password().isEmpty()) {
                // Anonymous bind
                return LdapResult.success();
            }
            String expected = credentials.get(normalizeDn(request.name()));
            if (expected != null && expected.equals(simple.password())) {
                return LdapResult.success();
            }
            return LdapResult.of(LdapResultCode.INVALID_CREDENTIALS, "Invalid credentials");
        }
        return LdapResult.of(LdapResultCode.AUTH_METHOD_NOT_SUPPORTED, "Only simple bind supported");
    }

    @Override
    public List<SearchResultEntry> search(SearchRequest request) {
        List<SearchResultEntry> results = new ArrayList<>();
        DistinguishedName baseDn = DistinguishedName.parse(request.baseObject());

        for (DirectoryEntry entry : entries.values()) {
            DistinguishedName entryDn = DistinguishedName.parse(entry.dn);

            boolean inScope = switch (request.scope()) {
                case BASE_OBJECT -> entryDn.equalsIgnoreCase(baseDn);
                case SINGLE_LEVEL -> {
                    DistinguishedName parent = entryDn.parent();
                    yield parent.equalsIgnoreCase(baseDn);
                }
                case WHOLE_SUBTREE -> entryDn.isUnder(baseDn);
            };

            if (inScope && matchesFilter(entry, request.filter())) {
                List<LdapAttribute> attrs = request.typesOnly() ?
                        entry.attributes.stream()
                                .map(a -> new LdapAttribute(a.type(), List.of()))
                                .toList() :
                        filterAttributes(entry.attributes, request.attributes());
                results.add(new SearchResultEntry(entry.dn, attrs));

                if (request.sizeLimit() > 0 && results.size() >= request.sizeLimit()) {
                    break;
                }
            }
        }
        return results;
    }

    @Override
    public boolean compare(CompareRequest request) {
        DirectoryEntry entry = entries.get(normalizeDn(request.entry()));
        if (entry == null) return false;

        for (LdapAttribute attr : entry.attributes) {
            if (attr.type().equalsIgnoreCase(request.attribute())) {
                for (byte[] val : attr.values()) {
                    if (Arrays.equals(val, request.value())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public LdapResult add(AddRequest request) {
        String normalizedDn = normalizeDn(request.entry());
        if (entries.containsKey(normalizedDn)) {
            return LdapResult.of(LdapResultCode.ENTRY_ALREADY_EXISTS, "Entry already exists");
        }
        entries.put(normalizedDn, new DirectoryEntry(request.entry(), new ArrayList<>(request.attributes())));
        return LdapResult.success();
    }

    @Override
    public LdapResult delete(DeleteRequest request) {
        String normalizedDn = normalizeDn(request.entry());
        if (entries.remove(normalizedDn) == null) {
            return LdapResult.of(LdapResultCode.NO_SUCH_OBJECT, "No such object");
        }
        return LdapResult.success();
    }

    @Override
    public LdapResult modify(ModifyRequest request) {
        String normalizedDn = normalizeDn(request.object());
        DirectoryEntry entry = entries.get(normalizedDn);
        if (entry == null) {
            return LdapResult.of(LdapResultCode.NO_SUCH_OBJECT, "No such object");
        }

        synchronized (entry) {
            for (ModifyRequest.Change change : request.changes()) {
                LdapAttribute mod = change.modification();
                switch (change.operation()) {
                    case ADD -> {
                        LdapAttribute existing = findAttribute(entry.attributes, mod.type());
                        if (existing != null) {
                            List<byte[]> merged = new ArrayList<>(existing.values());
                            merged.addAll(mod.values());
                            entry.attributes.remove(existing);
                            entry.attributes.add(new LdapAttribute(mod.type(), merged));
                        } else {
                            entry.attributes.add(mod);
                        }
                    }
                    case DELETE -> {
                        if (mod.values().isEmpty()) {
                            entry.attributes.removeIf(a -> a.type().equalsIgnoreCase(mod.type()));
                        } else {
                            LdapAttribute existing = findAttribute(entry.attributes, mod.type());
                            if (existing != null) {
                                List<byte[]> remaining = new ArrayList<>(existing.values());
                                for (byte[] toRemove : mod.values()) {
                                    remaining.removeIf(v -> Arrays.equals(v, toRemove));
                                }
                                entry.attributes.remove(existing);
                                if (!remaining.isEmpty()) {
                                    entry.attributes.add(new LdapAttribute(mod.type(), remaining));
                                }
                            }
                        }
                    }
                    case REPLACE -> {
                        entry.attributes.removeIf(a -> a.type().equalsIgnoreCase(mod.type()));
                        if (!mod.values().isEmpty()) {
                            entry.attributes.add(mod);
                        }
                    }
                }
            }
        }
        return LdapResult.success();
    }

    @Override
    public LdapResult modifyDn(ModifyDnRequest request) {
        String normalizedDn = normalizeDn(request.entry());
        DirectoryEntry entry = entries.remove(normalizedDn);
        if (entry == null) {
            return LdapResult.of(LdapResultCode.NO_SUCH_OBJECT, "No such object");
        }

        DistinguishedName oldDn = DistinguishedName.parse(request.entry());
        String newDnStr;
        if (request.newSuperior() != null) {
            newDnStr = request.newRdn() + "," + request.newSuperior();
        } else {
            DistinguishedName parent = oldDn.parent();
            newDnStr = parent.isEmpty() ? request.newRdn() : request.newRdn() + "," + parent;
        }

        entries.put(normalizeDn(newDnStr), new DirectoryEntry(newDnStr, entry.attributes));
        return LdapResult.success();
    }

    @Override
    public ExtendedResponse extended(ExtendedRequest request) {
        return new ExtendedResponse(
                LdapResult.of(LdapResultCode.UNWILLING_TO_PERFORM, "Extended operations not supported"),
                null, null);
    }

    // ── Filter matching ──

    private boolean matchesFilter(DirectoryEntry entry, SearchFilter filter) {
        return switch (filter) {
            case SearchFilter.And and -> and.filters().stream().allMatch(f -> matchesFilter(entry, f));
            case SearchFilter.Or or -> or.filters().stream().anyMatch(f -> matchesFilter(entry, f));
            case SearchFilter.Not not -> !matchesFilter(entry, not.filter());
            case SearchFilter.EqualityMatch eq -> hasAttributeValue(entry, eq.attribute(), eq.value());
            case SearchFilter.Present p -> hasAttribute(entry, p.attribute());
            case SearchFilter.Substrings sub -> matchesSubstring(entry, sub);
            case SearchFilter.GreaterOrEqual ge -> matchesComparison(entry, ge.attribute(), ge.value(), true);
            case SearchFilter.LessOrEqual le -> matchesComparison(entry, le.attribute(), le.value(), false);
            case SearchFilter.ApproxMatch am -> hasAttributeValue(entry, am.attribute(), am.value());
            case SearchFilter.ExtensibleMatch _ -> false; // Simplified: not implemented
        };
    }

    private boolean hasAttribute(DirectoryEntry entry, String attribute) {
        return entry.attributes.stream().anyMatch(a -> a.type().equalsIgnoreCase(attribute));
    }

    private boolean hasAttributeValue(DirectoryEntry entry, String attribute, byte[] value) {
        String strValue = new String(value, StandardCharsets.UTF_8);
        for (LdapAttribute attr : entry.attributes) {
            if (attr.type().equalsIgnoreCase(attribute)) {
                for (byte[] v : attr.values()) {
                    if (new String(v, StandardCharsets.UTF_8).equalsIgnoreCase(strValue)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchesSubstring(DirectoryEntry entry, SearchFilter.Substrings sub) {
        for (LdapAttribute attr : entry.attributes) {
            if (attr.type().equalsIgnoreCase(sub.attribute())) {
                for (byte[] v : attr.values()) {
                    String val = new String(v, StandardCharsets.UTF_8).toLowerCase();
                    boolean matches = true;
                    int pos = 0;
                    if (sub.initial() != null) {
                        if (!val.startsWith(sub.initial().toLowerCase())) {
                            matches = false;
                        } else {
                            pos = sub.initial().length();
                        }
                    }
                    if (matches) {
                        for (String any : sub.any()) {
                            int idx = val.indexOf(any.toLowerCase(), pos);
                            if (idx < 0) {
                                matches = false;
                                break;
                            }
                            pos = idx + any.length();
                        }
                    }
                    if (matches && sub.finalStr() != null) {
                        matches = val.endsWith(sub.finalStr().toLowerCase()) &&
                                val.length() - sub.finalStr().length() >= pos;
                    }
                    if (matches) return true;
                }
            }
        }
        return false;
    }

    private boolean matchesComparison(DirectoryEntry entry, String attribute, byte[] value, boolean greaterOrEqual) {
        String strValue = new String(value, StandardCharsets.UTF_8);
        for (LdapAttribute attr : entry.attributes) {
            if (attr.type().equalsIgnoreCase(attribute)) {
                for (byte[] v : attr.values()) {
                    String attrValue = new String(v, StandardCharsets.UTF_8);
                    int cmp = attrValue.compareToIgnoreCase(strValue);
                    if (greaterOrEqual ? cmp >= 0 : cmp <= 0) return true;
                }
            }
        }
        return false;
    }

    private List<LdapAttribute> filterAttributes(List<LdapAttribute> all, List<String> requested) {
        if (requested.isEmpty()) return all;
        return all.stream()
                .filter(a -> requested.stream().anyMatch(r -> r.equalsIgnoreCase(a.type())))
                .toList();
    }

    private LdapAttribute findAttribute(List<LdapAttribute> attrs, String type) {
        return attrs.stream()
                .filter(a -> a.type().equalsIgnoreCase(type))
                .findFirst().orElse(null);
    }

    private String normalizeDn(String dn) {
        return dn.toLowerCase().replaceAll("\\s*,\\s*", ",").replaceAll("\\s*=\\s*", "=").trim();
    }

    private static final class DirectoryEntry {
        final String dn;
        final List<LdapAttribute> attributes;

        DirectoryEntry(String dn, List<LdapAttribute> attributes) {
            this.dn = dn;
            this.attributes = attributes;
        }
    }
}
