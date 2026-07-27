package ssg.legoflow.network.ldap.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.ldap.client.LdapClient;
import ssg.legoflow.network.ldap.filter.SearchFilter;
import ssg.legoflow.network.ldap.protocol.*;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link LdapClient} and {@link LdapServer}.
 *
 * @since 1.0.0
 */
class LdapClientServerTest {

    private LdapServer server;
    private InMemoryDirectoryBackend backend;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        backend = new InMemoryDirectoryBackend();
        // Seed data
        backend.addEntry("dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "domain"),
                LdapAttribute.of("dc", "example")
        ));
        backend.addEntry("ou=People,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "top", "organizationalUnit"),
                LdapAttribute.of("ou", "People")
        ));
        backend.addEntry("cn=John Doe,ou=People,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "person", "inetOrgPerson"),
                LdapAttribute.of("cn", "John Doe"),
                LdapAttribute.of("sn", "Doe"),
                LdapAttribute.of("mail", "john@example.com"),
                LdapAttribute.of("uid", "jdoe")
        ));
        backend.addEntry("cn=Jane Smith,ou=People,dc=example,dc=com", List.of(
                LdapAttribute.of("objectClass", "person", "inetOrgPerson"),
                LdapAttribute.of("cn", "Jane Smith"),
                LdapAttribute.of("sn", "Smith"),
                LdapAttribute.of("mail", "jane@example.com"),
                LdapAttribute.of("uid", "jsmith")
        ));
        backend.setCredentials("cn=admin,dc=example,dc=com", "secret");

        server = LdapServer.start(0, backend);
        port = server.port();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) server.close();
    }

    @Test
    void testAnonymousBind() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            BindResponse resp = client.bindAnonymous();
            assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);
        }
    }

    @Test
    void testSimpleBind() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            BindResponse resp = client.bind("cn=admin,dc=example,dc=com", "secret");
            assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);
        }
    }

    @Test
    void testBindInvalidCredentials() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            BindResponse resp = client.bind("cn=admin,dc=example,dc=com", "wrong");
            assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.INVALID_CREDENTIALS);
        }
    }

    @Test
    void testSearchSubtree() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            List<SearchResultEntry> results = client.search(
                    "dc=example,dc=com",
                    SearchScope.WHOLE_SUBTREE,
                    SearchFilter.present("objectClass")
            );
            assertThat(results).hasSizeGreaterThanOrEqualTo(4);
        }
    }

    @Test
    void testSearchWithEqualityFilter() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            List<SearchResultEntry> results = client.search(
                    "dc=example,dc=com",
                    SearchScope.WHOLE_SUBTREE,
                    SearchFilter.equalityMatch("cn", "John Doe")
            );
            assertThat(results).hasSize(1);
            assertThat(results.get(0).objectName()).isEqualTo("cn=John Doe,ou=People,dc=example,dc=com");
        }
    }

    @Test
    void testSearchSingleLevel() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            List<SearchResultEntry> results = client.search(
                    "ou=People,dc=example,dc=com",
                    SearchScope.SINGLE_LEVEL,
                    SearchFilter.present("objectClass")
            );
            assertThat(results).hasSize(2); // John and Jane
        }
    }

    @Test
    void testSearchBaseObject() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            List<SearchResultEntry> results = client.search(
                    "cn=John Doe,ou=People,dc=example,dc=com",
                    SearchScope.BASE_OBJECT,
                    SearchFilter.present("objectClass")
            );
            assertThat(results).hasSize(1);
        }
    }

    @Test
    void testSearchWithAttributes() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            List<SearchResultEntry> results = client.search(
                    "dc=example,dc=com",
                    SearchScope.WHOLE_SUBTREE,
                    SearchFilter.equalityMatch("uid", "jdoe"),
                    List.of("cn", "mail")
            );
            assertThat(results).hasSize(1);
            SearchResultEntry entry = results.get(0);
            assertThat(entry.attributes()).hasSize(2);
        }
    }

    @Test
    void testCompare() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            boolean result = client.compare("cn=John Doe,ou=People,dc=example,dc=com", "cn", "John Doe");
            assertThat(result).isTrue();

            boolean noMatch = client.compare("cn=John Doe,ou=People,dc=example,dc=com", "cn", "Jane");
            assertThat(noMatch).isFalse();
        }
    }

    @Test
    void testAddAndDelete() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();

            // Add
            AddResponse addResp = client.add("cn=New User,ou=People,dc=example,dc=com",
                    List.of(
                            LdapAttribute.of("objectClass", "person"),
                            LdapAttribute.of("cn", "New User"),
                            LdapAttribute.of("sn", "User")
                    ));
            assertThat(addResp.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);

            // Verify
            List<SearchResultEntry> results = client.search(
                    "dc=example,dc=com",
                    SearchScope.WHOLE_SUBTREE,
                    SearchFilter.equalityMatch("cn", "New User")
            );
            assertThat(results).hasSize(1);

            // Delete
            DeleteResponse delResp = client.delete("cn=New User,ou=People,dc=example,dc=com");
            assertThat(delResp.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);

            // Verify deletion
            results = client.search(
                    "dc=example,dc=com",
                    SearchScope.WHOLE_SUBTREE,
                    SearchFilter.equalityMatch("cn", "New User")
            );
            assertThat(results).isEmpty();
        }
    }

    @Test
    void testModify() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();

            ModifyResponse resp = client.modify("cn=John Doe,ou=People,dc=example,dc=com",
                    List.of(new ModifyRequest.Change(
                            ModifyRequest.ModifyOperation.REPLACE,
                            LdapAttribute.of("mail", "newemail@example.com")
                    )));
            assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);

            // Verify
            List<SearchResultEntry> results = client.search(
                    "cn=John Doe,ou=People,dc=example,dc=com",
                    SearchScope.BASE_OBJECT,
                    SearchFilter.present("objectClass"),
                    List.of("mail")
            );
            assertThat(results).hasSize(1);
            assertThat(results.get(0).attributes().get(0).firstValueAsString())
                    .isEqualTo("newemail@example.com");
        }
    }

    @Test
    void testModifyDnRename() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();

            ModifyDnResponse resp = client.modifyDn(
                    "cn=Jane Smith,ou=People,dc=example,dc=com",
                    "cn=Janet Smith", true, null);
            assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);
        }
    }

    @Test
    void testUnbind() throws IOException {
        LdapClient client = LdapClient.connect("127.0.0.1", port);
        client.bindAnonymous();
        client.unbind(); // Should not throw
    }

    @Test
    void testSearchWithAndFilter() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            List<SearchResultEntry> results = client.search(
                    "dc=example,dc=com",
                    SearchScope.WHOLE_SUBTREE,
                    SearchFilter.and(
                            SearchFilter.equalityMatch("objectClass", "person"),
                            SearchFilter.equalityMatch("sn", "Doe")
                    )
            );
            assertThat(results).hasSize(1);
            assertThat(results.get(0).objectName()).contains("John Doe");
        }
    }

    @Test
    void testSearchWithOrFilter() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            List<SearchResultEntry> results = client.search(
                    "dc=example,dc=com",
                    SearchScope.WHOLE_SUBTREE,
                    SearchFilter.or(
                            SearchFilter.equalityMatch("cn", "John Doe"),
                            SearchFilter.equalityMatch("cn", "Jane Smith")
                    )
            );
            assertThat(results).hasSize(2);
        }
    }

    @Test
    void testSearchWithNotFilter() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            List<SearchResultEntry> results = client.search(
                    "ou=People,dc=example,dc=com",
                    SearchScope.SINGLE_LEVEL,
                    SearchFilter.not(SearchFilter.equalityMatch("cn", "John Doe"))
            );
            assertThat(results).hasSize(1);
            assertThat(results.get(0).objectName()).contains("Jane");
        }
    }

    @Test
    void testAddDuplicateEntryFails() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            AddResponse resp = client.add("cn=John Doe,ou=People,dc=example,dc=com",
                    List.of(LdapAttribute.of("cn", "John Doe")));
            assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.ENTRY_ALREADY_EXISTS);
        }
    }

    @Test
    void testDeleteNonExistentFails() throws IOException {
        try (LdapClient client = LdapClient.connect("127.0.0.1", port)) {
            client.bindAnonymous();
            DeleteResponse resp = client.delete("cn=Nobody,dc=example,dc=com");
            assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.NO_SUCH_OBJECT);
        }
    }
}
