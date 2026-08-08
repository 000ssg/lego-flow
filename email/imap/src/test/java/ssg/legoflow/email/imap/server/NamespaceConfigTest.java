package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class NamespaceConfigTest {
    private NamespaceConfig.NamespaceEntry entry(String pfx, String delim) {
        return new NamespaceConfig.NamespaceEntry(pfx, delim);
    }

    @Test void testDefaultNamespaces() {
        var ns = new NamespaceConfig(List.of(entry("~", "/")), List.of(), List.of());
        assertThat(ns.personal()).hasSize(1);
    }

    @Test void testPersonalNamespacePath() {
        var ns = new NamespaceConfig(List.of(entry("~/Mail", "/")), List.of(), List.of());
        assertThat(ns.personal()).isNotEmpty();
    }

    @Test void testOtherUser() {
        var ns = new NamespaceConfig(
                List.of(entry("~", "/")),
                List.of(entry("/", "/")),
                List.of());
        assertThat(ns.otherUsers()).hasSize(1);
    }

    @Test void testSharedNamespace() {
        var ns = new NamespaceConfig(
                List.of(entry("~", "/")),
                List.of(),
                List.of(entry("/shared", "/")));
        assertThat(ns.shared()).hasSize(1);
    }

    @Test void testEmptyNamespaces() {
        var ns = new NamespaceConfig(List.of(), List.of(), List.of());
        assertThat(ns.personal()).isEmpty();
        assertThat(ns.otherUsers()).isEmpty();
        assertThat(ns.shared()).isEmpty();
    }

    @Test void testNamespaceEntryFields() {
        var e = entry("~user", "/");
        assertThat(e.prefix()).isEqualTo("~user");
        assertThat(e.delimiter()).isEqualTo("/");
    }
}
