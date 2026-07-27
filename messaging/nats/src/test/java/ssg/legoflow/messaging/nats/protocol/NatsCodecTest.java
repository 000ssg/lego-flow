package ssg.legoflow.messaging.nats.protocol;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link NatsCodec}.
 */
class NatsCodecTest {

    // --- INFO ---

    @Test
    void testEncodeInfo() {
        var info = ServerInfo.withDefaults("SERVER1", "test-server", 4222);
        String encoded = NatsCodec.encodeInfo(info);

        assertThat(encoded).startsWith("INFO ");
        assertThat(encoded).endsWith("\r\n");
        assertThat(encoded).contains("\"server_id\":\"SERVER1\"");
        assertThat(encoded).contains("\"server_name\":\"test-server\"");
    }

    @Test
    void testDecodeInfo() throws IOException {
        var info = ServerInfo.withDefaults("SRV1", "my-server", 4222);
        String encoded = NatsCodec.encodeInfo(info);

        var op = parseOp(encoded);
        assertThat(op).isInstanceOf(NatsCodec.ParsedOp.Info.class);
        var parsed = (NatsCodec.ParsedOp.Info) op;
        assertThat(parsed.serverInfo().serverId()).isEqualTo("SRV1");
        assertThat(parsed.serverInfo().serverName()).isEqualTo("my-server");
        assertThat(parsed.serverInfo().port()).isEqualTo(4222);
    }

    @Test
    void testInfoRoundTrip() throws IOException {
        var info = new ServerInfo("ABC123", "nats-srv", "2.10.0",
                "127.0.0.1", 4222, 1048576, 1, true, true, false, false, 42);
        String encoded = NatsCodec.encodeInfo(info);
        var parsed = ((NatsCodec.ParsedOp.Info) parseOp(encoded)).serverInfo();

        assertThat(parsed.serverId()).isEqualTo("ABC123");
        assertThat(parsed.serverName()).isEqualTo("nats-srv");
        assertThat(parsed.version()).isEqualTo("2.10.0");
        assertThat(parsed.host()).isEqualTo("127.0.0.1");
        assertThat(parsed.port()).isEqualTo(4222);
        assertThat(parsed.maxPayload()).isEqualTo(1048576);
        assertThat(parsed.proto()).isEqualTo(1);
        assertThat(parsed.headers()).isTrue();
        assertThat(parsed.jetstream()).isTrue();
        assertThat(parsed.authRequired()).isFalse();
        assertThat(parsed.clientId()).isEqualTo(42);
    }

    // --- CONNECT ---

    @Test
    void testEncodeConnect() {
        var opts = ConnectOptions.withDefaults("test-client");
        String encoded = NatsCodec.encodeConnect(opts);

        assertThat(encoded).startsWith("CONNECT ");
        assertThat(encoded).endsWith("\r\n");
        assertThat(encoded).contains("\"name\":\"test-client\"");
    }

    @Test
    void testDecodeConnect() throws IOException {
        var opts = ConnectOptions.withDefaults("my-app").withVerbose(true);
        String encoded = NatsCodec.encodeConnect(opts);

        var op = parseOp(encoded);
        assertThat(op).isInstanceOf(NatsCodec.ParsedOp.Connect.class);
        var parsed = (NatsCodec.ParsedOp.Connect) op;
        assertThat(parsed.options().name()).isEqualTo("my-app");
        assertThat(parsed.options().verbose()).isTrue();
    }

    @Test
    void testConnectWithToken() throws IOException {
        var opts = ConnectOptions.withDefaults("client").withToken("s3cret");
        String encoded = NatsCodec.encodeConnect(opts);
        var parsed = ((NatsCodec.ParsedOp.Connect) parseOp(encoded)).options();

        assertThat(parsed.authToken()).isEqualTo("s3cret");
    }

    @Test
    void testConnectWithUserPass() throws IOException {
        var opts = ConnectOptions.withDefaults("client").withUserPass("admin", "password");
        String encoded = NatsCodec.encodeConnect(opts);
        var parsed = ((NatsCodec.ParsedOp.Connect) parseOp(encoded)).options();

        assertThat(parsed.user()).isEqualTo("admin");
        assertThat(parsed.pass()).isEqualTo("password");
    }

    // --- PUB ---

    @Test
    void testEncodePubNoReply() {
        String encoded = NatsCodec.encodePub("foo.bar", null, "hello".getBytes());
        assertThat(encoded).isEqualTo("PUB foo.bar 5\r\nhello\r\n");
    }

    @Test
    void testEncodePubWithReply() {
        String encoded = NatsCodec.encodePub("foo.bar", "_INBOX.123", "hi".getBytes());
        assertThat(encoded).isEqualTo("PUB foo.bar _INBOX.123 2\r\nhi\r\n");
    }

    @Test
    void testEncodePubEmptyPayload() {
        String encoded = NatsCodec.encodePub("test", null, new byte[0]);
        assertThat(encoded).isEqualTo("PUB test 0\r\n\r\n");
    }

    @Test
    void testDecodePub() throws IOException {
        String raw = "PUB foo.bar 5\r\nhello\r\n";
        var op = parseOp(raw);
        assertThat(op).isInstanceOf(NatsCodec.ParsedOp.Pub.class);
        var pub = (NatsCodec.ParsedOp.Pub) op;
        assertThat(pub.subject()).isEqualTo("foo.bar");
        assertThat(pub.replyTo()).isNull();
        assertThat(new String(pub.payload())).isEqualTo("hello");
    }

    @Test
    void testDecodePubWithReply() throws IOException {
        String raw = "PUB foo.bar _INBOX.1 3\r\nabc\r\n";
        var pub = (NatsCodec.ParsedOp.Pub) parseOp(raw);
        assertThat(pub.subject()).isEqualTo("foo.bar");
        assertThat(pub.replyTo()).isEqualTo("_INBOX.1");
        assertThat(new String(pub.payload())).isEqualTo("abc");
    }

    @Test
    void testDecodePubEmptyPayload() throws IOException {
        String raw = "PUB test 0\r\n\r\n";
        var pub = (NatsCodec.ParsedOp.Pub) parseOp(raw);
        assertThat(pub.payload()).isEmpty();
    }

    // --- HPUB ---

    @Test
    void testEncodeHpub() {
        var headers = new NatsHeaders();
        headers.set("X-Type", "event");
        String encoded = NatsCodec.encodeHpub("events.new", null, headers, "data".getBytes());

        assertThat(encoded).startsWith("HPUB events.new ");
        assertThat(encoded).contains("NATS/1.0");
        assertThat(encoded).contains("X-Type: event");
    }

    @Test
    void testDecodeHpub() throws IOException {
        var headers = new NatsHeaders();
        headers.set("Key", "Value");
        String encoded = NatsCodec.encodeHpub("test", null, headers, "payload".getBytes());

        var op = parseOp(encoded);
        assertThat(op).isInstanceOf(NatsCodec.ParsedOp.Hpub.class);
        var hpub = (NatsCodec.ParsedOp.Hpub) op;
        assertThat(hpub.subject()).isEqualTo("test");
        assertThat(hpub.headers().getFirst("Key")).isEqualTo("Value");
        assertThat(new String(hpub.payload())).isEqualTo("payload");
    }

    @Test
    void testDecodeHpubWithReply() throws IOException {
        var headers = new NatsHeaders();
        headers.set("X-Id", "123");
        String encoded = NatsCodec.encodeHpub("req", "_INBOX.abc", headers, "body".getBytes());

        var hpub = (NatsCodec.ParsedOp.Hpub) parseOp(encoded);
        assertThat(hpub.subject()).isEqualTo("req");
        assertThat(hpub.replyTo()).isEqualTo("_INBOX.abc");
    }

    // --- SUB ---

    @Test
    void testEncodeSub() {
        String encoded = NatsCodec.encodeSub("foo.bar", null, "1");
        assertThat(encoded).isEqualTo("SUB foo.bar 1\r\n");
    }

    @Test
    void testEncodeSubWithQueue() {
        String encoded = NatsCodec.encodeSub("foo.bar", "workers", "2");
        assertThat(encoded).isEqualTo("SUB foo.bar workers 2\r\n");
    }

    @Test
    void testDecodeSub() throws IOException {
        var sub = (NatsCodec.ParsedOp.Sub) parseOp("SUB foo.bar 1\r\n");
        assertThat(sub.subject()).isEqualTo("foo.bar");
        assertThat(sub.queueGroup()).isNull();
        assertThat(sub.sid()).isEqualTo("1");
    }

    @Test
    void testDecodeSubWithQueue() throws IOException {
        var sub = (NatsCodec.ParsedOp.Sub) parseOp("SUB foo.bar workers 2\r\n");
        assertThat(sub.subject()).isEqualTo("foo.bar");
        assertThat(sub.queueGroup()).isEqualTo("workers");
        assertThat(sub.sid()).isEqualTo("2");
    }

    // --- UNSUB ---

    @Test
    void testEncodeUnsub() {
        assertThat(NatsCodec.encodeUnsub("1", -1)).isEqualTo("UNSUB 1\r\n");
    }

    @Test
    void testEncodeUnsubWithMax() {
        assertThat(NatsCodec.encodeUnsub("1", 5)).isEqualTo("UNSUB 1 5\r\n");
    }

    @Test
    void testDecodeUnsub() throws IOException {
        var unsub = (NatsCodec.ParsedOp.Unsub) parseOp("UNSUB 1\r\n");
        assertThat(unsub.sid()).isEqualTo("1");
        assertThat(unsub.maxMsgs()).isEqualTo(-1);
    }

    @Test
    void testDecodeUnsubWithMax() throws IOException {
        var unsub = (NatsCodec.ParsedOp.Unsub) parseOp("UNSUB 3 10\r\n");
        assertThat(unsub.sid()).isEqualTo("3");
        assertThat(unsub.maxMsgs()).isEqualTo(10);
    }

    // --- MSG ---

    @Test
    void testEncodeMsg() {
        String encoded = NatsCodec.encodeMsg("foo", "1", null, "data".getBytes());
        assertThat(encoded).isEqualTo("MSG foo 1 4\r\ndata\r\n");
    }

    @Test
    void testEncodeMsgWithReply() {
        String encoded = NatsCodec.encodeMsg("foo", "1", "_INBOX.x", "ok".getBytes());
        assertThat(encoded).isEqualTo("MSG foo 1 _INBOX.x 2\r\nok\r\n");
    }

    @Test
    void testDecodeMsg() throws IOException {
        var msg = (NatsCodec.ParsedOp.Msg) parseOp("MSG foo 1 4\r\ndata\r\n");
        assertThat(msg.subject()).isEqualTo("foo");
        assertThat(msg.sid()).isEqualTo("1");
        assertThat(msg.replyTo()).isNull();
        assertThat(new String(msg.payload())).isEqualTo("data");
    }

    @Test
    void testDecodeMsgWithReply() throws IOException {
        var msg = (NatsCodec.ParsedOp.Msg) parseOp("MSG foo 1 _INBOX.x 2\r\nok\r\n");
        assertThat(msg.replyTo()).isEqualTo("_INBOX.x");
    }

    // --- HMSG ---

    @Test
    void testEncodeDecodeHmsg() throws IOException {
        var headers = new NatsHeaders();
        headers.set("X-Test", "value");
        String encoded = NatsCodec.encodeHmsg("subj", "5", null, headers, "body".getBytes());

        var hmsg = (NatsCodec.ParsedOp.Hmsg) parseOp(encoded);
        assertThat(hmsg.subject()).isEqualTo("subj");
        assertThat(hmsg.sid()).isEqualTo("5");
        assertThat(hmsg.headers().getFirst("X-Test")).isEqualTo("value");
        assertThat(new String(hmsg.payload())).isEqualTo("body");
    }

    @Test
    void testHmsgWithReply() throws IOException {
        var headers = new NatsHeaders();
        headers.set("H", "V");
        String encoded = NatsCodec.encodeHmsg("s", "1", "reply.to", headers, "p".getBytes());

        var hmsg = (NatsCodec.ParsedOp.Hmsg) parseOp(encoded);
        assertThat(hmsg.replyTo()).isEqualTo("reply.to");
    }

    // --- PING / PONG ---

    @Test
    void testEncodePing() {
        assertThat(NatsCodec.encodePing()).isEqualTo("PING\r\n");
    }

    @Test
    void testEncodePong() {
        assertThat(NatsCodec.encodePong()).isEqualTo("PONG\r\n");
    }

    @Test
    void testDecodePing() throws IOException {
        assertThat(parseOp("PING\r\n")).isInstanceOf(NatsCodec.ParsedOp.Ping.class);
    }

    @Test
    void testDecodePong() throws IOException {
        assertThat(parseOp("PONG\r\n")).isInstanceOf(NatsCodec.ParsedOp.Pong.class);
    }

    // --- +OK / -ERR ---

    @Test
    void testEncodeOk() {
        assertThat(NatsCodec.encodeOk()).isEqualTo("+OK\r\n");
    }

    @Test
    void testEncodeErr() {
        assertThat(NatsCodec.encodeErr("Bad Subject"))
                .isEqualTo("-ERR 'Bad Subject'\r\n");
    }

    @Test
    void testDecodeOk() throws IOException {
        assertThat(parseOp("+OK\r\n")).isInstanceOf(NatsCodec.ParsedOp.Ok.class);
    }

    @Test
    void testDecodeErr() throws IOException {
        var err = (NatsCodec.ParsedOp.Err) parseOp("-ERR 'Authorization Violation'\r\n");
        assertThat(err.message()).isEqualTo("Authorization Violation");
    }

    @Test
    void testDecodeErrWithoutQuotes() throws IOException {
        var err = (NatsCodec.ParsedOp.Err) parseOp("-ERR Unknown Protocol Operation\r\n");
        assertThat(err.message()).isEqualTo("Unknown Protocol Operation");
    }

    // --- EOF ---

    @Test
    void testReadOpReturnsNullOnEof() throws IOException {
        var reader = new BufferedReader(new StringReader(""));
        assertThat(NatsCodec.readOp(reader)).isNull();
    }

    // --- Unknown ---

    @Test
    void testReadOpThrowsOnUnknown() {
        assertThatThrownBy(() -> parseOp("UNKNOWN_OP\r\n"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unknown NATS operation");
    }

    // --- Round trips ---

    @Test
    void testPubRoundTrip() throws IOException {
        byte[] payload = "test payload 123".getBytes();
        String encoded = NatsCodec.encodePub("test.subject", "_INBOX.reply", payload);
        var pub = (NatsCodec.ParsedOp.Pub) parseOp(encoded);

        assertThat(pub.subject()).isEqualTo("test.subject");
        assertThat(pub.replyTo()).isEqualTo("_INBOX.reply");
        assertThat(pub.payload()).isEqualTo(payload);
    }

    @Test
    void testSubRoundTrip() throws IOException {
        String encoded = NatsCodec.encodeSub("events.>", "my-group", "42");
        var sub = (NatsCodec.ParsedOp.Sub) parseOp(encoded);

        assertThat(sub.subject()).isEqualTo("events.>");
        assertThat(sub.queueGroup()).isEqualTo("my-group");
        assertThat(sub.sid()).isEqualTo("42");
    }

    // Helper

    private NatsCodec.ParsedOp parseOp(String raw) throws IOException {
        var reader = new BufferedReader(new StringReader(raw));
        return NatsCodec.readOp(reader);
    }
}
