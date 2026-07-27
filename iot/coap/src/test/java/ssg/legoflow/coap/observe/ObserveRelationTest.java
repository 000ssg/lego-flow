package ssg.legoflow.coap.observe;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObserveRelation}.
 *
 * @since 1.0.0
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
}
