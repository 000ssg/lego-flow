package ssg.legoflow.http.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class WebSocketCloseHandshakeTest {

    private WebSocketSession session;
    private final ArrayList<WebSocketFrame> sentFrames = new ArrayList<>();

    @BeforeEach
    void setUp() {
        session = new WebSocketSession("test-close");
        session.setFrameSender(sentFrames::add);
    }

    @Test
    void testCloseWithStatusCode() {
        // Given
        var closedFrame = new AtomicReference<WebSocketFrame>();
        session.onClose(closedFrame::set);

        // When — receive a close frame with status code
        session.handleFrame(WebSocketFrame.close(1000, "Normal Closure"));

        // Then
        assertThat(session.isOpen()).isFalse();
        assertThat(session.getCloseState()).isEqualTo(WebSocketSession.CloseState.CLOSED);
        assertThat(session.getCloseCode()).isEqualTo(1000);
        assertThat(session.getCloseReason()).isEqualTo("Normal Closure");
        // Should have sent a close frame back
        assertThat(sentFrames).hasSize(1);
        assertThat(sentFrames.getFirst().getCloseCode()).isEqualTo(1000);
    }

    @Test
    void testInitiateCloseWithCode() {
        // When
        session.close(1001, "Going Away");

        // Then — should be in CLOSING state until peer responds
        assertThat(session.getCloseState()).isEqualTo(WebSocketSession.CloseState.CLOSING);
        assertThat(sentFrames).hasSize(1);
        assertThat(sentFrames.getFirst().getCloseCode()).isEqualTo(1001);
    }

    @Test
    void testFullCloseHandshake() {
        // Given
        var closedFrame = new AtomicReference<WebSocketFrame>();
        session.onClose(closedFrame::set);

        // When — initiate close
        session.close(1000, "Normal Closure");
        assertThat(session.getCloseState()).isEqualTo(WebSocketSession.CloseState.CLOSING);

        // When — receive close response from peer
        session.handleFrame(WebSocketFrame.close(1000, "Normal Closure"));

        // Then
        assertThat(session.getCloseState()).isEqualTo(WebSocketSession.CloseState.CLOSED);
        assertThat(session.isOpen()).isFalse();
    }

    @Test
    void testCloseWithWebSocketCloseCode() {
        // When
        session.close(WebSocketCloseCode.PROTOCOL_ERROR);

        // Then
        assertThat(session.getCloseCode()).isEqualTo(1002);
        assertThat(sentFrames).hasSize(1);
    }

    @Test
    void testCloseFramePayloadEncoding() {
        // When
        var frame = WebSocketFrame.close(1000, "Bye");

        // Then
        assertThat(frame.getCloseCode()).isEqualTo(1000);
        assertThat(frame.getCloseReason()).isEqualTo("Bye");
    }

    @Test
    void testCloseFrameWithoutReason() {
        // When
        var frame = WebSocketFrame.close(1000, null);

        // Then
        assertThat(frame.getCloseCode()).isEqualTo(1000);
        assertThat(frame.getCloseReason()).isEmpty();
    }

    @Test
    void testEmptyCloseFrameReturnsMinusOne() {
        // When
        var frame = WebSocketFrame.close();

        // Then
        assertThat(frame.getCloseCode()).isEqualTo(-1);
        assertThat(frame.getCloseReason()).isEmpty();
    }

    @Test
    void testCloseWithoutFrameSender() {
        // Given — no frame sender set
        var sessionNoSender = new WebSocketSession("no-sender");
        var closedFrame = new AtomicReference<WebSocketFrame>();
        sessionNoSender.onClose(closedFrame::set);

        // When
        sessionNoSender.close(1000, "Bye");

        // Then — should go directly to CLOSED
        assertThat(sessionNoSender.getCloseState()).isEqualTo(WebSocketSession.CloseState.CLOSED);
        assertThat(sessionNoSender.isOpen()).isFalse();
        assertThat(closedFrame.get()).isNotNull();
    }
}
