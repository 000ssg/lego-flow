package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class DnsNameTest {

    @Test
    void testRootDomain() {
        DnsName root = DnsName.ROOT;
        assertThat(root.isRoot()).isTrue();
        assertThat(root.labels()).isEmpty();
        assertThat(root.toString()).isEqualTo(".");
        assertThat(root.toFqdn()).isEqualTo(".");
        assertThat(root.wireLength()).isEqualTo(1);
    }

    @Test
    void testOfSimpleName() {
        DnsName name = DnsName.of("www.example.com");
        assertThat(name.labels()).containsExactly("www", "example", "com");
        assertThat(name.labelCount()).isEqualTo(3);
        assertThat(name.toString()).isEqualTo("www.example.com");
    }

    @Test
    void testOfWithTrailingDot() {
        DnsName name = DnsName.of("www.example.com.");
        assertThat(name.labels()).containsExactly("www", "example", "com");
        assertThat(name.toString()).isEqualTo("www.example.com");
    }

    @Test
    void testOfEmptyReturnsRoot() {
        assertThat(DnsName.of("")).isEqualTo(DnsName.ROOT);
        assertThat(DnsName.of(".")).isEqualTo(DnsName.ROOT);
    }

    @Test
    void testOfSingleLabel() {
        DnsName name = DnsName.of("localhost");
        assertThat(name.labels()).containsExactly("localhost");
        assertThat(name.labelCount()).isEqualTo(1);
    }

    @Test
    void testOfRejectsEmptyLabel() {
        assertThatThrownBy(() -> DnsName.of("www..com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testOfRejectsLongLabel() {
        String longLabel = "a".repeat(64);
        assertThatThrownBy(() -> DnsName.of(longLabel + ".com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("63 bytes");
    }

    @Test
    void testFromLabels() {
        DnsName name = DnsName.fromLabels(List.of("www", "example", "com"));
        assertThat(name.labels()).containsExactly("www", "example", "com");
    }

    @Test
    void testFromLabelsEmptyReturnsRoot() {
        assertThat(DnsName.fromLabels(List.of())).isEqualTo(DnsName.ROOT);
    }

    @Test
    void testParent() {
        DnsName name = DnsName.of("www.example.com");
        DnsName parent = name.parent();
        assertThat(parent.toString()).isEqualTo("example.com");

        DnsName grandparent = parent.parent();
        assertThat(grandparent.toString()).isEqualTo("com");

        assertThat(grandparent.parent()).isEqualTo(DnsName.ROOT);
        assertThat(DnsName.ROOT.parent()).isEqualTo(DnsName.ROOT);
    }

    @Test
    void testIsSubdomainOf() {
        DnsName www = DnsName.of("www.example.com");
        DnsName example = DnsName.of("example.com");
        DnsName com = DnsName.of("com");

        assertThat(www.isSubdomainOf(example)).isTrue();
        assertThat(www.isSubdomainOf(com)).isTrue();
        assertThat(www.isSubdomainOf(DnsName.ROOT)).isTrue();
        assertThat(example.isSubdomainOf(www)).isFalse();
        assertThat(www.isSubdomainOf(DnsName.of("other.com"))).isFalse();
    }

    @Test
    void testCaseInsensitiveEquality() {
        DnsName lower = DnsName.of("www.example.com");
        DnsName upper = DnsName.of("WWW.EXAMPLE.COM");
        DnsName mixed = DnsName.of("Www.Example.Com");

        assertThat(lower).isEqualTo(upper);
        assertThat(lower).isEqualTo(mixed);
        assertThat(lower.hashCode()).isEqualTo(upper.hashCode());
    }

    @Test
    void testWildcardMatching() {
        DnsName wildcard = DnsName.of("*.example.com");
        DnsName www = DnsName.of("www.example.com");
        DnsName mail = DnsName.of("mail.example.com");
        DnsName deep = DnsName.of("sub.www.example.com");

        assertThat(www.matchesWildcard(wildcard)).isTrue();
        assertThat(mail.matchesWildcard(wildcard)).isTrue();
        assertThat(deep.matchesWildcard(wildcard)).isFalse(); // different depth
    }

    @Test
    void testWildcardNonWildcard() {
        DnsName name = DnsName.of("www.example.com");
        DnsName other = DnsName.of("www.example.com");
        DnsName diff = DnsName.of("mail.example.com");

        assertThat(name.matchesWildcard(other)).isTrue();
        assertThat(name.matchesWildcard(diff)).isFalse();
    }

    @Test
    void testPrepend() {
        DnsName name = DnsName.of("example.com");
        DnsName prepended = name.prepend("www");
        assertThat(prepended.toString()).isEqualTo("www.example.com");
    }

    @Test
    void testWireLength() {
        DnsName name = DnsName.of("www.example.com");
        // 3 + "www" + 7 + "example" + 3 + "com" + 0 = 1+3+1+7+1+3+1 = 17
        assertThat(name.wireLength()).isEqualTo(17);
    }

    @Test
    void testToFqdn() {
        DnsName name = DnsName.of("www.example.com");
        assertThat(name.toFqdn()).isEqualTo("www.example.com.");
    }

    @Test
    void testToCanonical() {
        DnsName name = DnsName.of("WWW.Example.COM");
        assertThat(name.toCanonical()).isEqualTo("www.example.com");
    }

    @Test
    void testCompareTo() {
        DnsName a = DnsName.of("a.example.com");
        DnsName b = DnsName.of("b.example.com");
        assertThat(a.compareTo(b)).isLessThan(0);
        assertThat(b.compareTo(a)).isGreaterThan(0);
        assertThat(a.compareTo(a)).isZero();
    }

    @Test
    void testNullRejected() {
        assertThatThrownBy(() -> DnsName.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.com", "b.c.d.e.f", "x", "test-name.example.org"})
    void testRoundTripParsing(String input) {
        DnsName name = DnsName.of(input);
        assertThat(name.toString()).isEqualTo(input);
    }

    @Test
    void testMaxLabelLength() {
        String label63 = "a".repeat(63);
        DnsName name = DnsName.of(label63 + ".com");
        assertThat(name.labels().get(0)).hasSize(63);
    }
}
