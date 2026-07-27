package ssg.legoflow.http.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class WebSocketSessionTest {

    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        session = new WebSocketSession("test-session");
    }

    @Test
    void testSessionIdAndInitialState() {
        assertThat(session.getId()).isEqualTo("test-session");
        assertThat(session.isOpen()).isTrue();
    }

    @Test
    void testHandleTextFrame() {
        var received = new ArrayList<String>();
        session.onMessage(frame -> received.add(frame.getPayloadText()));

        session.handleFrame(WebSocketFrame.text("Hello"));

        assertThat(received).containsExactly("Hello");
    }

    @Test
    void testHandleMultipleFrames() {
        var received = new ArrayList<String>();
        session.onMessage(frame -> received.add(frame.getPayloadText()));

        session.handleFrame(WebSocketFrame.text("msg1"));
        session.handleFrame(WebSocketFrame.text("msg2"));
        session.handleFrame(WebSocketFrame.text("msg3"));

        assertThat(received).containsExactly("msg1", "msg2", "msg3");
    }

    @Test
    void testCloseFrame() {
        var closed = new AtomicBoolean(false);
        session.onClose(frame -> closed.set(true));

        session.handleFrame(WebSocketFrame.close());

        assertThat(session.isOpen()).isFalse();
        assertThat(closed.get()).isTrue();
    }

    @Test
    void testCloseWithoutHandler() {
        session.handleFrame(WebSocketFrame.close());

        assertThat(session.isOpen()).isFalse();
    }

    @Test
    void testManualClose() {
        var closed = new AtomicBoolean(false);
        session.onClose(frame -> closed.set(true));

        session.close();

        assertThat(session.isOpen()).isFalse();
        assertThat(closed.get()).isTrue();
    }

    @Test
    void testDoubleCloseCallsHandlerOnce() {
        var closeCount = new java.util.concurrent.atomic.AtomicInteger(0);
        session.onClose(frame -> closeCount.incrementAndGet());

        session.close();
        session.close();

        assertThat(closeCount.get()).isEqualTo(1);
    }

    @Test
    void testFramesIgnoredAfterClose() {
        var received = new ArrayList<String>();
        session.onMessage(frame -> received.add(frame.getPayloadText()));

        session.close();
        session.handleFrame(WebSocketFrame.text("ignored"));

        assertThat(received).isEmpty();
    }

    @Test
    void testErrorHandler() {
        var error = new AtomicReference<Throwable>();
        session.onError(error::set);

        session.handleError(new RuntimeException("test error"));

        assertThat(error.get()).isInstanceOf(RuntimeException.class)
                .hasMessage("test error");
    }

    @Test
    void testPingFrameDoesNotTriggerMessageHandler() {
        var received = new ArrayList<WebSocketFrame>();
        session.onMessage(received::add);

        session.handleFrame(WebSocketFrame.ping(new byte[]{1, 2, 3}));

        assertThat(received).isEmpty();
    }

    @Test
    void testPongFrameDoesNotTriggerMessageHandler() {
        var received = new ArrayList<WebSocketFrame>();
        session.onMessage(received::add);

        session.handleFrame(WebSocketFrame.pong(new byte[]{1, 2, 3}));

        assertThat(received).isEmpty();
    }
}
