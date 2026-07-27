package ssg.legoflow.xmpp.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link JID}.
 *
 * @since 1.0.0
 */
class JIDTest {

    @Test
    void testParseFullJid() {
        var jid = JID.parse("user@example.com/resource");
        assertThat(jid.localpart()).isEqualTo("user");
        assertThat(jid.domainpart()).isEqualTo("example.com");
        assertThat(jid.resourcepart()).isEqualTo("resource");
    }

    @Test
    void testParseBareJid() {
        var jid = JID.parse("user@example.com");
        assertThat(jid.localpart()).isEqualTo("user");
        assertThat(jid.domainpart()).isEqualTo("example.com");
        assertThat(jid.resourcepart()).isNull();
    }

    @Test
    void testParseDomainOnly() {
        var jid = JID.parse("example.com");
        assertThat(jid.localpart()).isNull();
        assertThat(jid.domainpart()).isEqualTo("example.com");
        assertThat(jid.resourcepart()).isNull();
    }

    @Test
    void testToBareJid() {
        var jid = JID.parse("user@example.com/resource");
        assertThat(jid.toBareJid()).isEqualTo("user@example.com");
    }

    @Test
    void testToFullJid() {
        var jid = JID.parse("user@example.com/resource");
        assertThat(jid.toFullJid()).isEqualTo("user@example.com/resource");
    }

    @Test
    void testToBare() {
        var jid = JID.parse("user@example.com/resource");
        var bare = jid.toBare();
        assertThat(bare.resourcepart()).isNull();
        assertThat(bare.toBareJid()).isEqualTo("user@example.com");
    }

    @Test
    void testWithResource() {
        var jid = JID.parse("user@example.com");
        var full = jid.withResource("mobile");
        assertThat(full.resourcepart()).isEqualTo("mobile");
        assertThat(full.toFullJid()).isEqualTo("user@example.com/mobile");
    }

    @Test
    void testHasLocalpartAndResourcepart() {
        var full = JID.parse("user@example.com/res");
        assertThat(full.hasLocalpart()).isTrue();
        assertThat(full.hasResourcepart()).isTrue();

        var domain = JID.parse("example.com");
        assertThat(domain.hasLocalpart()).isFalse();
        assertThat(domain.hasResourcepart()).isFalse();
    }

    @Test
    void testInvalidDomain() {
        assertThatThrownBy(() -> JID.parse("user@"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullJidString() {
        assertThatThrownBy(() -> JID.parse(null))
                .isInstanceOf(NullPointerException.class);
    }
}
