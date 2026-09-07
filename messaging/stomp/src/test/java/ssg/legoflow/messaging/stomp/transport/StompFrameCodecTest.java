package ssg.legoflow.messaging.stomp.transport;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.messaging.stomp.core.StompCommand;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.core.StompHeaders;
import ssg.legoflow.messaging.stomp.core.StompCodec;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

class StompFrameCodecTest {

    private InMemoryTransport[] pair;
    private StompFrameCodec serverCodec;  // reads from pair[0] (receives what client sends)
    private StompFrameCodec clientCodec;  // reads from pair[1] (receives what server sends)

    @BeforeEach
    void setUp() {
        pair = InMemoryTransport.createPair();
        serverCodec = new StompFrameCodec(pair[0]);
        clientCodec = new StompFrameCodec(pair[1]);
    }

    @Test
    void testClientSendsServerReceives() throws Exception {
        var headers = new StompHeaders();
        headers.put("destination", "/topic/test");
        var frame = StompFrame.withText(StompCommand.SEND, headers, "Hello");
        clientCodec.send(frame);

        var received = serverCodec.receive();
        assertThat(received).isNotNull();
        assertThat(received.command()).isEqualTo(StompCommand.SEND);
        assertThat(received.header("destination")).isEqualTo("/topic/test");
        assertThat(received.bodyAsText()).isEqualTo("Hello");
    }

    @Test
    void testServerSendsClientReceives() throws Exception {
        var headers = new StompHeaders();
        headers.put(StompHeaders.VERSION, "1.2");
        var frame = new StompFrame(StompCommand.CONNECTED, headers);
        serverCodec.send(frame);

        var received = clientCodec.receive();
        assertThat(received).isNotNull();
        assertThat(received.command()).isEqualTo(StompCommand.CONNECTED);
        assertThat(received.header(StompHeaders.VERSION)).isEqualTo("1.2");
    }

    @Test
    void testGetTransport() {
        assertThat(serverCodec.getTransport()).isEqualTo(pair[0]);
        assertThat(clientCodec.getTransport()).isEqualTo(pair[1]);
    }

    @Test
    void testTransportIsIndependent() {
        // Codec does not own transport lifecycle — transport stays open after codec use
        clientCodec.send(StompFrame.withText(StompCommand.SEND, new StompHeaders(), "test"));
        assertThat(pair[1].isOpen()).isTrue();
        assertThat(pair[0].isOpen()).isTrue();
    }

    @Test
    void testMultipleFrames() throws Exception {
        clientCodec.send(StompFrame.withText(StompCommand.SEND, new StompHeaders(), "msg1"));
        clientCodec.send(StompFrame.withText(StompCommand.SEND, new StompHeaders(), "msg2"));

        var f1 = serverCodec.receive();
        var f2 = serverCodec.receive();
        assertThat(f1.command()).isEqualTo(StompCommand.SEND);
        assertThat(f2.command()).isEqualTo(StompCommand.SEND);
        assertThat(f1.bodyAsText()).isEqualTo("msg1");
        assertThat(f2.bodyAsText()).isEqualTo("msg2");
    }

    @Test
    void testEncodeDecodeRoundTrip() throws Exception {
        var headers = new StompHeaders();
        headers.put(StompHeaders.LOGIN, "admin");
        headers.put(StompHeaders.PASSCODE, "secret");
        var frame = new StompFrame(StompCommand.CONNECT, headers);
        clientCodec.send(frame);

        // Server receives the frame
        var received = serverCodec.receive();
        assertThat(received.command()).isEqualTo(StompCommand.CONNECT);
        assertThat(received.header(StompHeaders.LOGIN)).isEqualTo("admin");
        assertThat(received.header(StompHeaders.PASSCODE)).isEqualTo("secret");
    }

    @Test
    void testEncodeDecodeEmptyBody() throws Exception {
        var headers = new StompHeaders();
        var frame = new StompFrame(StompCommand.BEGIN, headers);
        clientCodec.send(frame);

        var received = serverCodec.receive();
        assertThat(received).isNotNull();
        assertThat(received.command()).isEqualTo(StompCommand.BEGIN);
    }

    @Test
    void testLargeBody() throws Exception {
        var headers = new StompHeaders();
        var body = "X".repeat(10000);
        var frame = StompFrame.withText(StompCommand.SEND, headers, body);
        clientCodec.send(frame);

        var received = serverCodec.receive();
        assertThat(received.bodyAsText()).hasSize(10000);
    }

    @Test
    void testReceiveTimeoutOnTransport() throws Exception {
        var buf = ByteBuffer.allocate(1024);
        int n = pair[1].receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test
    void testCloseTransport() {
        pair[0].close();
        assertThat(pair[0].isOpen()).isFalse();
        // Pair[1] is independent
        assertThat(pair[1].isOpen()).isTrue();
    }
}
