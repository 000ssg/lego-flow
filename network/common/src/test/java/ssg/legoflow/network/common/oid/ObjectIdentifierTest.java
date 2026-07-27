package ssg.legoflow.network.common.oid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ObjectIdentifier}.
 */
class ObjectIdentifierTest {

    @Test
    void testParseSimple() {
        ObjectIdentifier oid = ObjectIdentifier.parse("1.3.6.1.2.1.1");
        assertThat(oid.size()).isEqualTo(7);
        assertThat(oid.arc(0)).isEqualTo(1);
        assertThat(oid.arc(1)).isEqualTo(3);
        assertThat(oid.arc(6)).isEqualTo(1);
    }

    @Test
    void testParseTwoArcs() {
        ObjectIdentifier oid = ObjectIdentifier.parse("2.5");
        assertThat(oid.size()).isEqualTo(2);
        assertThat(oid.arc(0)).isEqualTo(2);
        assertThat(oid.arc(1)).isEqualTo(5);
    }

    @Test
    void testParseFirstArc0() {
        ObjectIdentifier oid = ObjectIdentifier.parse("0.9");
        assertThat(oid.arc(0)).isEqualTo(0);
        assertThat(oid.arc(1)).isEqualTo(9);
    }

    @Test
    void testParseFirstArc2LargeSecond() {
        // Arc 2 can have second > 39
        ObjectIdentifier oid = ObjectIdentifier.parse("2.999");
        assertThat(oid.arc(0)).isEqualTo(2);
        assertThat(oid.arc(1)).isEqualTo(999);
    }

    @Test
    void testToDottedString() {
        ObjectIdentifier oid = ObjectIdentifier.parse("1.3.6.1.2.1.1.1");
        assertThat(oid.toDottedString()).isEqualTo("1.3.6.1.2.1.1.1");
    }

    @Test
    void testOf() {
        ObjectIdentifier oid = ObjectIdentifier.of(1, 3, 6, 1);
        assertThat(oid.toDottedString()).isEqualTo("1.3.6.1");
    }

    @Test
    void testStartsWith() {
        ObjectIdentifier oid = ObjectIdentifier.parse("1.3.6.1.2.1.1");
        ObjectIdentifier prefix = ObjectIdentifier.parse("1.3.6.1");
        assertThat(oid.startsWith(prefix)).isTrue();
        assertThat(prefix.startsWith(oid)).isFalse();
    }

    @Test
    void testStartsWithSelf() {
        ObjectIdentifier oid = ObjectIdentifier.parse("1.3.6.1");
        assertThat(oid.startsWith(oid)).isTrue();
    }

    @Test
    void testChild() {
        ObjectIdentifier parent = ObjectIdentifier.parse("1.3.6.1");
        ObjectIdentifier child = parent.child(2);
        assertThat(child.toDottedString()).isEqualTo("1.3.6.1.2");
    }

    @Test
    void testEquals() {
        ObjectIdentifier a = ObjectIdentifier.parse("1.3.6.1");
        ObjectIdentifier b = ObjectIdentifier.of(1, 3, 6, 1);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testNotEquals() {
        ObjectIdentifier a = ObjectIdentifier.parse("1.3.6.1");
        ObjectIdentifier b = ObjectIdentifier.parse("1.3.6.2");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void testCompareTo() {
        ObjectIdentifier a = ObjectIdentifier.parse("1.3.6.1");
        ObjectIdentifier b = ObjectIdentifier.parse("1.3.6.2");
        ObjectIdentifier c = ObjectIdentifier.parse("1.3.6.1.1");
        assertThat(a.compareTo(b)).isNegative();
        assertThat(b.compareTo(a)).isPositive();
        assertThat(a.compareTo(c)).isNegative(); // shorter prefix
        assertThat(a.compareTo(a)).isZero();
    }

    @Test
    void testToString() {
        ObjectIdentifier oid = ObjectIdentifier.parse("2.5.4.3");
        assertThat(oid.toString()).isEqualTo("2.5.4.3");
    }

    // ── Validation ──

    @Test
    void testParseNull() {
        assertThatThrownBy(() -> ObjectIdentifier.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseEmpty() {
        assertThatThrownBy(() -> ObjectIdentifier.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseSingleArc() {
        assertThatThrownBy(() -> ObjectIdentifier.parse("1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseInvalidFirstArc() {
        assertThatThrownBy(() -> ObjectIdentifier.parse("3.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseSecondArcTooLarge() {
        assertThatThrownBy(() -> ObjectIdentifier.parse("0.40"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObjectIdentifier.parse("1.40"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testOfTooFewArcs() {
        assertThatThrownBy(() -> ObjectIdentifier.of(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testArcsReturnsCopy() {
        ObjectIdentifier oid = ObjectIdentifier.parse("1.3.6.1");
        int[] arcs = oid.arcs();
        arcs[0] = 99;
        assertThat(oid.arc(0)).isEqualTo(1);
    }
}
