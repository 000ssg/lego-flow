package ssg.legoflow.coap.observe;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ObserveRelation}.
 *
 * @since 0.1.0
 */
class ObserveRelationTest {

    @Test
    void testCreateRelation() {
        var token = new byte[]{0x01, 0x02};
        var observer = new InetSocketAddress("localhost", 5683);
        var relation = new ObserveRelation(token, "/sensors/temp", observer);

        assertThat(relation.token()).containsExactly(0x01, 0x02);
        assertThat(relation.resourcePath()).isEqualTo("/sensors/temp");
        assertThat(relation.observer()).isEqualTo(observer);
        assertThat(relation.isActive()).isTrue();
        assertThat(relation.sequenceNumber()).isZero();
    }

    @Test
    void testCancel() {
        var relation = new ObserveRelation(new byte[]{0x01}, "/path", new InetSocketAddress(5683));
        assertThat(relation.isActive()).isTrue();

        relation.cancel();
        assertThat(relation.isActive()).isFalse();
    }

    @Test
    void testSequenceNumber() {
        var relation = new ObserveRelation(new byte[]{0x01}, "/path", new InetSocketAddress(5683));

        assertThat(relation.sequenceNumber()).isZero();
        assertThat(relation.nextSequenceNumber()).isEqualTo(1);
        assertThat(relation.nextSequenceNumber()).isEqualTo(2);
        assertThat(relation.sequenceNumber()).isEqualTo(2);
    }

    @Test
    void testTokenDefensiveCopy() {
        var token = new byte[]{0x01, 0x02};
        var relation = new ObserveRelation(token, "/path", new InetSocketAddress(5683));

        // Modify original
        token[0] = (byte) 0xFF;

        // Relation should be unchanged
        assertThat(relation.token()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void testEqualsSameInstance() {
        var relation = new ObserveRelation(new byte[]{0x01}, "/path", new InetSocketAddress(5683));
        assertThat(relation).isEqualTo(relation);
    }

    @Test
    void testEqualsEqualValues() {
        var obs = new InetSocketAddress(5683);
        var r1 = new ObserveRelation(new byte[]{0x01}, "/path", obs);
        var r2 = new ObserveRelation(new byte[]{0x01}, "/path", obs);

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void testEqualsDifferentToken() {
        var obs = new InetSocketAddress(5683);
        var r1 = new ObserveRelation(new byte[]{0x01}, "/path", obs);
        var r2 = new ObserveRelation(new byte[]{0x02}, "/path", obs);

        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void testEqualsDifferentPath() {
        var obs = new InetSocketAddress(5683);
        var r1 = new ObserveRelation(new byte[]{0x01}, "/path", obs);
        var r2 = new ObserveRelation(new byte[]{0x01}, "/other", obs);

        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void testEqualsDifferentObserver() {
        var r1 = new ObserveRelation(new byte[]{0x01}, "/path", new InetSocketAddress(5683));
        var r2 = new ObserveRelation(new byte[]{0x01}, "/path", new InetSocketAddress(5684));

        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void testEqualsNullAndNonInstance() {
        var relation = new ObserveRelation(new byte[]{0x01}, "/path", new InetSocketAddress(5683));
        assertThat(relation).isNotEqualTo(null);
        assertThat(relation).isNotEqualTo("string");
    }

    @Test
    void testHashCodeConsistent() {
        var obs = new InetSocketAddress(5683);
        var r1 = new ObserveRelation(new byte[]{0x01}, "/path", obs);
        var r2 = new ObserveRelation(new byte[]{0x01}, "/path", obs);

        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void testToStringContainsInfo() {
        var relation = new ObserveRelation(new byte[]{0x01}, "/sensors/temp", new InetSocketAddress("localhost", 5683));
        relation.nextSequenceNumber();
        String str = relation.toString();

        assertThat(str).contains("ObserveRelation");
        assertThat(str).contains("/sensors/temp");
        assertThat(str).contains("seq=1");
        assertThat(str).contains("active=true");
    }

    @Test
    void testToStringAfterCancel() {
        var relation = new ObserveRelation(new byte[]{0x01}, "/path", new InetSocketAddress(5683));
        relation.cancel();
        assertThat(relation.toString()).contains("active=false");
    }

    @Test
    void testNullTokenThrows() {
        assertThatThrownBy(() -> new ObserveRelation(null, "/path", new InetSocketAddress(5683)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullPathThrows() {
        assertThatThrownBy(() -> new ObserveRelation(new byte[]{0x01}, null, new InetSocketAddress(5683)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullObserverThrows() {
        assertThatThrownBy(() -> new ObserveRelation(new byte[]{0x01}, "/path", (java.net.SocketAddress) null))
                .isInstanceOf(NullPointerException.class);
    }
}
