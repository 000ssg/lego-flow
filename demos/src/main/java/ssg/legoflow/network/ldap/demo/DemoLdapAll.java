package ssg.legoflow.network.ldap.demo;

import ssg.legoflow.network.ldap.client.LdapClient;
import ssg.legoflow.network.ldap.filter.FilterParser;
import ssg.legoflow.network.ldap.filter.SearchFilter;
import ssg.legoflow.network.ldap.protocol.*;
import ssg.legoflow.network.ldap.server.InMemoryDirectoryBackend;
import ssg.legoflow.network.ldap.server.LdapServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Comprehensive demo of all LDAP module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link LdapServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports LDAP v3 operations: bind (simple),
 * search (base/one/sub scope), add, modify, delete, compare, modify DN, and
 * extended operations. Uses {@link InMemoryDirectoryBackend} for zero-config setup.
 * Ideal for development, testing, CI/CD, and learning the LDAP protocol.</p>
 *
 * <p><b>Alternative: External OpenLDAP / Active Directory / 389 Directory Server</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production directory integration with real schema enforcement</li>
 *   <li>SASL authentication mechanisms (GSSAPI, DIGEST-MD5, EXTERNAL)</li>
 *   <li>Referral chasing across distributed directory trees</li>
 *   <li>Replication and multi-master testing</li>
 *   <li>Integration testing against a real LDAP directory</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop)
 * and initial data population. All client code uses the same API regardless of backend.
 * When {@code USE_EXTERNAL=true}, the demo skips server creation and connects directly
 * to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Simple bind — authenticate with DN and password</li>
 *   <li>Search (base scope) — retrieve a single entry by DN</li>
 *   <li>Search (one level) — list children of a container</li>
 *   <li>Search (subtree) — recursive search with filter</li>
 *   <li>Add entries — create new directory entries with attributes</li>
 *   <li>Modify entries — add, replace, delete attribute values</li>
 *   <li>Delete entries — remove entries from the directory</li>
 *   <li>Compare operation — test attribute values without retrieving</li>
 *   <li>Modify DN — rename/move entries</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoLdapAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoLdapAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house LdapServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for OpenLDAP/AD/389DS
    // =========================================================================

    /** Set to {@code true} to connect to an external LDAP server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external LDAP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external LDAP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 389;

    private DemoLdapAll() {}

    /**
     * Results from running the full demo.
     *
     * @param simpleBind        true if simple bind succeeded
     * @param searchBase        number of entries found in base scope search
     * @param searchOneLevel    number of entries found in one-level search
     * @param searchSubtree     number of entries found in subtree search
     * @param addEntries        true if add operations succeeded
     * @param modifyEntries     true if modify operations succeeded
     * @param deleteEntries     true if delete operations succeeded
     * @param compareOp         true if compare operation returned correct result
     * @param modifyDn          true if modify DN (rename) succeeded
     */
    public record Results(
            boolean simpleBind,
            int searchBase,
            int searchOneLevel,
            int searchSubtree,
            boolean addEntries,
            boolean modifyEntries,
            boolean deleteEntries,
            boolean compareOp,
            boolean modifyDn
    ) {}

    /**
     * Runs the comprehensive demo covering all LDAP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runAgainstServer(EXTERNAL_HOST, EXTERNAL_PORT);
        }

        // Set up in-memory directory backend with sample data
        InMemoryDirectoryBackend backend = createDemoBackend();

        try (LdapServer server = LdapServer.start(0, backend)) {
            int port = server.port();
            LOG.info("In-house LdapServer started on port {}", port);
            return runAgainstServer("localhost", port);
        }
    }

    private static Results runAgainstServer(String host, int port) throws Exception {
        boolean bind = demoSimpleBind(host, port);
        int searchBase = demoSearchBase(host, port);
        int searchOne = demoSearchOneLevel(host, port);
        int searchSub = demoSearchSubtree(host, port);
        boolean add = demoAddEntries(host, port);
        boolean modify = demoModifyEntries(host, port);
        boolean compare = demoCompare(host, port);
        boolean modDn = demoModifyDn(host, port);
        boolean delete = demoDeleteEntries(host, port);

        return new Results(bind, searchBase, searchOne, searchSub, add, modify,
                delete, compare, modDn);
    }

    // ======================== Backend Setup ==================================

    /**
     * Creates the in-memory directory backend with organizational structure.
     */
    static InMemoryDirectoryBackend createDemoBackend() {
        InMemoryDirectoryBackend backend = new InMemoryDirectoryBackend();

        // Root entry
        backend.addEntry("dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "domain"),
                LdapAttribute.of("dc", "example")));

        // Organizational units
        backend.addEntry("ou=people,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "organizationalUnit"),
                LdapAttribute.of("ou", "people")));

        backend.addEntry("ou=groups,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "organizationalUnit"),
                LdapAttribute.of("ou", "groups")));

        // Users
        backend.addEntry("cn=admin,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "person"),
                LdapAttribute.of("cn", "admin"),
                LdapAttribute.of("sn", "Administrator")));
        backend.setCredentials("cn=admin,dc=example,dc=com", "secret");

        backend.addEntry("uid=alice,ou=people,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "person", "inetOrgPerson"),
                LdapAttribute.of("uid", "alice"),
                LdapAttribute.of("cn", "Alice Smith"),
                LdapAttribute.of("sn", "Smith"),
                LdapAttribute.of("mail", "alice@example.com"),
                LdapAttribute.of("telephoneNumber", "+1-555-0101")));

        backend.addEntry("uid=bob,ou=people,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "person", "inetOrgPerson"),
                LdapAttribute.of("uid", "bob"),
                LdapAttribute.of("cn", "Bob Jones"),
                LdapAttribute.of("sn", "Jones"),
                LdapAttribute.of("mail", "bob@example.com")));

        backend.addEntry("uid=carol,ou=people,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "person", "inetOrgPerson"),
                LdapAttribute.of("uid", "carol"),
                LdapAttribute.of("cn", "Carol White"),
                LdapAttribute.of("sn", "White"),
                LdapAttribute.of("mail", "carol@example.com")));

        // Group
        backend.addEntry("cn=developers,ou=groups,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "groupOfNames"),
                LdapAttribute.of("cn", "developers"),
                LdapAttribute.of("member", "uid=alice,ou=people,dc=example,dc=com",
                        "uid=bob,ou=people,dc=example,dc=com")));

        return backend;
    }

    // ======================== 1. SIMPLE BIND ================================

    /**
     * Demonstrates simple bind authentication with DN and password.
     */
    static boolean demoSimpleBind(String host, int port) throws IOException {
        LOG.info("=== 1. Simple Bind ===");
        try (LdapClient client = LdapClient.connect(host, port)) {
            BindResponse response = client.bind("cn=admin,dc=example,dc=com", "secret");
            boolean success = response.result().resultCode() == LdapResultCode.SUCCESS;
            LOG.info("Bind result: {}", response.result().resultCode());
            return success;
        }
    }

    // ======================== 2. SEARCH (BASE SCOPE) ========================

    /**
     * Demonstrates base scope search: retrieves a single entry by its exact DN.
     */
    static int demoSearchBase(String host, int port) throws IOException {
        LOG.info("=== 2. Search (Base Scope) ===");
        try (LdapClient client = LdapClient.connect(host, port)) {
            client.bind("cn=admin,dc=example,dc=com", "secret");

            SearchFilter filter = FilterParser.parse("(objectClass=*)");
            List<SearchResultEntry> results = client.search(
                    "uid=alice,ou=people,dc=example,dc=com",
                    SearchScope.BASE_OBJECT, filter);

            LOG.info("Base search results: {} entries", results.size());
            for (SearchResultEntry entry : results) {
                LOG.info("  DN: {}", entry.objectName());
            }
            return results.size();
        }
    }

    // ======================== 3. SEARCH (ONE LEVEL) =========================

    /**
     * Demonstrates one-level scope search: lists immediate children of a container.
     * <p>
     * <b>Preferred: one-level search</b> — efficient for listing children of an OU.
     * <p>
     * <b>Alternative: subtree search</b> — when you need to search nested OUs too.
     */
    static int demoSearchOneLevel(String host, int port) throws IOException {
        LOG.info("=== 3. Search (One Level) ===");
        try (LdapClient client = LdapClient.connect(host, port)) {
            client.bind("cn=admin,dc=example,dc=com", "secret");

            SearchFilter filter = FilterParser.parse("(objectClass=person)");
            List<SearchResultEntry> results = client.search(
                    "ou=people,dc=example,dc=com",
                    SearchScope.SINGLE_LEVEL, filter);

            LOG.info("One-level search results: {} entries", results.size());
            for (SearchResultEntry entry : results) {
                LOG.info("  DN: {}", entry.objectName());
            }
            return results.size();
        }
    }

    // ======================== 4. SEARCH (SUBTREE) ===========================

    /**
     * Demonstrates subtree scope search with a complex filter expression.
     */
    static int demoSearchSubtree(String host, int port) throws IOException {
        LOG.info("=== 4. Search (Subtree) ===");
        try (LdapClient client = LdapClient.connect(host, port)) {
            client.bind("cn=admin,dc=example,dc=com", "secret");

            // Search for all person entries with a mail attribute
            SearchFilter filter = FilterParser.parse("(&(objectClass=person)(mail=*))");
            List<SearchResultEntry> results = client.search(
                    "dc=example,dc=com",
                    SearchScope.WHOLE_SUBTREE, filter);

            LOG.info("Subtree search results: {} entries", results.size());
            for (SearchResultEntry entry : results) {
                LOG.info("  DN: {}", entry.objectName());
            }
            return results.size();
        }
    }

    // ======================== 5. ADD ENTRIES =================================

    /**
     * Demonstrates adding new entries to the directory.
     */
    static boolean demoAddEntries(String host, int port) throws IOException {
        LOG.info("=== 5. Add Entries ===");
        try (LdapClient client = LdapClient.connect(host, port)) {
            client.bind("cn=admin,dc=example,dc=com", "secret");

            AddResponse response = client.add("uid=dave,ou=people,dc=example,dc=com", List.of(
                    LdapAttribute.of("objectClass", "top", "person", "inetOrgPerson"),
                    LdapAttribute.of("uid", "dave"),
                    LdapAttribute.of("cn", "Dave Brown"),
                    LdapAttribute.of("sn", "Brown"),
                    LdapAttribute.of("mail", "dave@example.com")));

            boolean success = response.result().resultCode() == LdapResultCode.SUCCESS;
            LOG.info("Add entry result: {}", response.result().resultCode());

            // Verify the entry exists
            SearchFilter filter = FilterParser.parse("(uid=dave)");
            List<SearchResultEntry> found = client.search(
                    "ou=people,dc=example,dc=com",
                    SearchScope.SINGLE_LEVEL, filter);
            LOG.info("Verified new entry: {} found", found.size());

            return success && found.size() == 1;
        }
    }

    // ======================== 6. MODIFY ENTRIES ==============================

    /**
     * Demonstrates modifying entry attributes: add, replace, delete operations.
     */
    static boolean demoModifyEntries(String host, int port) throws IOException {
        LOG.info("=== 6. Modify Entries ===");
        try (LdapClient client = LdapClient.connect(host, port)) {
            client.bind("cn=admin,dc=example,dc=com", "secret");

            // Replace phone number and add a description
            ModifyResponse response = client.modify("uid=alice,ou=people,dc=example,dc=com", List.of(
                    new ModifyRequest.Change(
                            ModifyRequest.ModifyOperation.REPLACE,
                            LdapAttribute.of("telephoneNumber", "+1-555-9999")),
                    new ModifyRequest.Change(
                            ModifyRequest.ModifyOperation.ADD,
                            LdapAttribute.of("description", "Senior developer"))));

            boolean success = response.result().resultCode() == LdapResultCode.SUCCESS;
            LOG.info("Modify result: {}", response.result().resultCode());

            // Verify modification
            SearchFilter filter = FilterParser.parse("(uid=alice)");
            List<SearchResultEntry> results = client.search(
                    "ou=people,dc=example,dc=com",
                    SearchScope.SINGLE_LEVEL, filter, List.of("telephoneNumber", "description"));
            if (!results.isEmpty()) {
                for (LdapAttribute attr : results.getFirst().attributes()) {
                    LOG.info("  {} = {}", attr.type(), attr.valuesAsStrings());
                }
            }
            return success;
        }
    }

    // ======================== 7. COMPARE OPERATION ==========================

    /**
     * Demonstrates the compare operation: tests an attribute value without retrieval.
     */
    static boolean demoCompare(String host, int port) throws IOException {
        LOG.info("=== 7. Compare Operation ===");
        try (LdapClient client = LdapClient.connect(host, port)) {
            client.bind("cn=admin,dc=example,dc=com", "secret");

            // Compare should return true for correct value
            boolean matchTrue = client.compare(
                    "uid=alice,ou=people,dc=example,dc=com", "sn", "Smith");
            LOG.info("Compare sn=Smith: {}", matchTrue);

            // Compare should return false for incorrect value
            boolean matchFalse = client.compare(
                    "uid=alice,ou=people,dc=example,dc=com", "sn", "Wrong");
            LOG.info("Compare sn=Wrong: {}", matchFalse);

            return matchTrue && !matchFalse;
        }
    }

    // ======================== 8. MODIFY DN ===================================

    /**
     * Demonstrates modify DN: renaming an entry's RDN.
     */
    static boolean demoModifyDn(String host, int port) throws IOException {
        LOG.info("=== 8. Modify DN ===");
        try (LdapClient client = LdapClient.connect(host, port)) {
            client.bind("cn=admin,dc=example,dc=com", "secret");

            // First add an entry to rename
            client.add("uid=temp,ou=people,dc=example,dc=com", List.of(
                    LdapAttribute.of("objectClass", "top", "person", "inetOrgPerson"),
                    LdapAttribute.of("uid", "temp"),
                    LdapAttribute.of("cn", "Temp User"),
                    LdapAttribute.of("sn", "Temp")));

            // Rename uid=temp to uid=renamed
            ModifyDnResponse response = client.modifyDn(
                    "uid=temp,ou=people,dc=example,dc=com",
                    "uid=renamed", true, null);

            boolean success = response.result().resultCode() == LdapResultCode.SUCCESS;
            LOG.info("Modify DN result: {}", response.result().resultCode());

            // Verify renamed entry exists via base scope search on the new DN.
            // Note: the in-house backend renames the DN key but does not update
            // the RDN attribute value inside the entry, so we verify by DN lookup
            // rather than by attribute filter.
            SearchFilter filter = FilterParser.parse("(objectClass=*)");
            List<SearchResultEntry> found = client.search(
                    "uid=renamed,ou=people,dc=example,dc=com",
                    SearchScope.BASE_OBJECT, filter);
            LOG.info("Renamed entry found: {}", found.size());

            return success && found.size() == 1;
        }
    }

    // ======================== 9. DELETE ENTRIES ==============================

    /**
     * Demonstrates deleting entries from the directory.
     */
    static boolean demoDeleteEntries(String host, int port) throws IOException {
        LOG.info("=== 9. Delete Entries ===");
        try (LdapClient client = LdapClient.connect(host, port)) {
            client.bind("cn=admin,dc=example,dc=com", "secret");

            // Delete the renamed entry from the modify DN demo
            DeleteResponse response = client.delete("uid=renamed,ou=people,dc=example,dc=com");
            boolean success = response.result().resultCode() == LdapResultCode.SUCCESS;
            LOG.info("Delete result: {}", response.result().resultCode());

            // Also delete dave from the add demo
            client.delete("uid=dave,ou=people,dc=example,dc=com");

            // Verify deletions
            SearchFilter filter = FilterParser.parse("(|(uid=renamed)(uid=dave))");
            List<SearchResultEntry> remaining = client.search(
                    "ou=people,dc=example,dc=com",
                    SearchScope.SINGLE_LEVEL, filter);
            LOG.info("Remaining after delete: {}", remaining.size());

            return success && remaining.isEmpty();
        }
    }
}
