package ssg.legoflow.xmpp.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class XmppCoreTest {
    @Test void testJidParse() {
        var jid = JID.parse("user@domain.com/resource");
        assertThat(jid.localpart()).isEqualTo("user");
        assertThat(jid.domainpart()).isEqualTo("domain.com");
        assertThat(jid.resourcepart()).isEqualTo("resource");
    }

    @Test void testJidParseNoResource() {
        var jid = JID.parse("user@domain.com");
        assertThat(jid.localpart()).isEqualTo("user");
    }

    @Test void testJidCreation() {
        var jid = new JID("user", "domain.com", "resource");
        assertThat(jid.toString()).contains("user@domain.com");
    }
}
