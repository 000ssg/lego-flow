package ssg.legoflow.wamp.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for new WAMP message types: Challenge, Authenticate, Event, Cancel, Interrupt.
 */
class NewMessageTypesTest {

    private final WampSerializer serializer = new WampSerializer();

    @Test
    void testChallengeMessageType() {
        var msg = new WampMessage.Challenge("wampcra", Map.of("challenge", "xyz"));
        assertThat(msg.type()).isEqualTo(WampMessageType.CHALLENGE);
        assertThat(msg.type().code()).isEqualTo(4);
    }

    @Test
    void testAuthenticateMessageType() {
        var msg = new WampMessage.Authenticate("sig123", Map.of());
        assertThat(msg.type()).isEqualTo(WampMessageType.AUTHENTICATE);
        assertThat(msg.type().code()).isEqualTo(5);
    }

    @Test
    void testEventMessageType() {
        var msg = new WampMessage.Event(1L, 2L, Map.of(), List.of("data"));
        assertThat(msg.type()).isEqualTo(WampMessageType.EVENT);
        assertThat(msg.type().code()).isEqualTo(36);
    }

    @Test
    void testCancelMessageType() {
        var msg = new WampMessage.Cancel(1L, Map.of("mode", "kill"));
        assertThat(msg.type()).isEqualTo(WampMessageType.CANCEL);
        assertThat(msg.type().code()).isEqualTo(49);
    }

    @Test
    void testInterruptMessageType() {
        var msg = new WampMessage.Interrupt(1L, Map.of("mode", "killnowait"));
        assertThat(msg.type()).isEqualTo(WampMessageType.INTERRUPT);
        assertThat(msg.type().code()).isEqualTo(69);
    }

    @Test
    void testChallengeJsonRoundTrip() {
        var msg = new WampMessage.Challenge("ticket", Map.of("extra", "data"));
        var json = serializer.serialize(msg);
        var result = (WampMessage.Challenge) serializer.deserialize(json);
        assertThat(result.authMethod()).isEqualTo("ticket");
        assertThat(result.extra()).containsEntry("extra", "data");
    }

    @Test
    void testAuthenticateJsonRoundTrip() {
        var msg = new WampMessage.Authenticate("my-signature", Map.of());
        var json = serializer.serialize(msg);
        var result = (WampMessage.Authenticate) serializer.deserialize(json);
        assertThat(result.signature()).isEqualTo("my-signature");
    }

    @Test
    void testEventJsonRoundTrip() {
        var msg = new WampMessage.Event(100L, 200L, Map.of("publisher", 5), List.of("evt-data"));
        var json = serializer.serialize(msg);
        var result = (WampMessage.Event) serializer.deserialize(json);
        assertThat(result.subscriptionId()).isEqualTo(100L);
        assertThat(result.publicationId()).isEqualTo(200L);
        assertThat(result.args()).containsExactly("evt-data");
    }

    @Test
    void testCancelJsonRoundTrip() {
        var msg = new WampMessage.Cancel(42L, Map.of("mode", "skip"));
        var json = serializer.serialize(msg);
        var result = (WampMessage.Cancel) serializer.deserialize(json);
        assertThat(result.requestId()).isEqualTo(42L);
    }

    @Test
    void testInterruptJsonRoundTrip() {
        var msg = new WampMessage.Interrupt(7L, Map.of("mode", "kill"));
        var json = serializer.serialize(msg);
        var result = (WampMessage.Interrupt) serializer.deserialize(json);
        assertThat(result.requestId()).isEqualTo(7L);
    }

    @Test
    void testWampMessageTypeFromCode() {
        assertThat(WampMessageType.fromCode(4)).isEqualTo(WampMessageType.CHALLENGE);
        assertThat(WampMessageType.fromCode(5)).isEqualTo(WampMessageType.AUTHENTICATE);
        assertThat(WampMessageType.fromCode(36)).isEqualTo(WampMessageType.EVENT);
        assertThat(WampMessageType.fromCode(49)).isEqualTo(WampMessageType.CANCEL);
        assertThat(WampMessageType.fromCode(69)).isEqualTo(WampMessageType.INTERRUPT);
    }

    @Test
    void testWampSessionAuthFields() {
        var session = new WampSession();
        session.establish(1L, "realm1");
        session.setAuthId("user1");
        session.setAuthRole("admin");
        session.setAuthMethod("wampcra");

        assertThat(session.getAuthId()).isEqualTo("user1");
        assertThat(session.getAuthRole()).isEqualTo("admin");
        assertThat(session.getAuthMethod()).isEqualTo("wampcra");
    }

    @Test
    void testWampSessionAuthFieldsDefaultNull() {
        var session = new WampSession();
        session.establish(1L, "realm1");

        assertThat(session.getAuthId()).isNull();
        assertThat(session.getAuthRole()).isNull();
        assertThat(session.getAuthMethod()).isNull();
    }
}
