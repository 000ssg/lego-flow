package ssg.legoflow.upnp.gena;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
class GenaSubscriberExtendedTest {

    @Test void eventMessageBasicConstruction() {
        Map<String, String> vars = Map.of("Volume", "50");
        var msg = new EventMessage("sid-1", 1, vars);
        assertThat(msg.sid()).isEqualTo("sid-1");
        assertThat(msg.seq()).isEqualTo(1);
    }

    @Test void eventMessageInitialEventHasSeqZero() {
        var msg = new EventMessage("sid-1", 0, Map.of("state", "on"));
        assertThat(msg.isInitialEvent()).isTrue();
    }

    @Test void eventMessageNonInitialEvent() {
        var msg = new EventMessage("sid-1", 1, Map.of("state", "off"));
        assertThat(msg.isInitialEvent()).isFalse();
    }

    @Test void eventMessageNullSidThrows() {
        assertThatThrownBy(() -> new EventMessage(null, 1, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void eventMessageNullMapThrows() {
        assertThatThrownBy(() -> new EventMessage("sid", 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void eventMessageNegativeSeqThrows() {
        assertThatThrownBy(() -> new EventMessage("sid", -1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void eventMessageCopiesVariablesMap() {
        var original = new LinkedHashMap<String, String>();
        original.put("Volume", "50");
        var msg = new EventMessage("sid-1", 1, original);
        original.put("Volume", "99");
        assertThat(msg.changedVariables()).containsEntry("Volume", "50");
    }

    @Test void eventMessageToXml() {
        Map<String, String> vars = Map.of("Volume", "50");
        var msg = new EventMessage("sid-1", 1, vars);
        String xml = msg.toXml();
        assertThat(xml).contains("<?xml");
    }

    @Test void eventMessageParseXml() {
        String xml = "<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">"
                + "<e:property><Volume>75</Volume></e:property>"
                + "</e:propertyset>";
        var msg = EventMessage.parseXml("sid-test", 3, xml);
        assertThat(msg.changedVariables()).containsEntry("Volume", "75");
    }

    @Test void eventSubscriptionConstruction() throws Exception {
        var sub = new EventSubscription(
                "sub-1",
                URI.create("http://host/notify"),
                URI.create("http://host/sub"),
                "uuid::service:1",
                Duration.ofMinutes(5),
                Instant.now().plus(Duration.ofMinutes(5)));
        assertThat(sub.sid()).isEqualTo("sub-1");
    }

    @Test void genaConstantsValues() {
        assertThat(GenaConstants.HEADER_SID).isEqualTo("SID");
        assertThat(GenaConstants.HEADER_SEQ).isEqualTo("SEQ");
    }
}
