package ssg.legoflow.http.websocket;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages a WebSocket session with full close handshake per RFC 6455 §7.
 *
 * <p>The close handshake involves exchanging close frames with status codes.
 * The initiator sends a close frame, the peer responds with a close frame,
 * then the connection is closed.
 *
 * @since 0.1.0
 */
public class WebSocketSession {

    /**
     * Represents the close handshake state per RFC 6455 §7.1.
     *
     * @since 0.1.0
     */
    public enum CloseState {
        /** Connection is open, no close initiated. */
        OPEN,
        /** This endpoint sent a close frame, awaiting peer's close frame. */
        CLOSING,
        /** Close handshake is complete. */
        CLOSED
    }

    private final String id;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicReference<CloseState> closeState = new AtomicReference<>(CloseState.OPEN);
    private Consumer<WebSocketFrame> onMessage;
    private Consumer<WebSocketFrame> onClose;
    private Consumer<Throwable> onError;
    private Consumer<WebSocketFrame> frameSender;
    private int closeCode = -1;
    private String closeReason = "";

    public WebSocketSession(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public boolean isOpen() { return open.get(); }

    /**
     * Returns the current close handshake state.
     *
     * @return the close state
     */
    public CloseState getCloseState() { return closeState.get(); }

    /**
     * Returns the close status code received or sent.
     *
     * @return the close code, or -1 if not yet closed
     */
    public int getCloseCode() { return closeCode; }

    /**
     * Returns the close reason string.
     *
     * @return the close reason
     */
    public String getCloseReason() { return closeReason; }

    public void onMessage(Consumer<WebSocketFrame> handler) { this.onMessage = handler; }
    public void onClose(Consumer<WebSocketFrame> handler) { this.onClose = handler; }
    public void onError(Consumer<Throwable> handler) { this.onError = handler; }

    /**
     * Sets the frame sender for sending close response frames.
     *
     * @param sender a consumer that sends frames to the remote peer
     */
    public void setFrameSender(Consumer<WebSocketFrame> sender) { this.frameSender = sender; }

    public void handleFrame(WebSocketFrame frame) {
        if (!open.get()) return;
        switch (frame.getOpCode()) {
            case CLOSE -> handleCloseFrame(frame);
            case PING -> { /* handled by codec layer */ }
            case PONG -> { /* ignore */ }
            default -> { if (onMessage != null) onMessage.accept(frame); }
        }
    }

    private void handleCloseFrame(WebSocketFrame closeFrame) {
        int code = closeFrame.getCloseCode();
        String reason = closeFrame.getCloseReason();

        if (closeState.compareAndSet(CloseState.OPEN, CloseState.CLOSED)) {
            // Received close from peer — we need to respond with close frame
            this.closeCode = code >= 0 ? code : WebSocketCloseCode.NORMAL_CLOSURE.code();
            this.closeReason = reason;
            // Send close frame back (echo the code)
            if (frameSender != null) {
                frameSender.accept(WebSocketFrame.close(this.closeCode, this.closeReason));
            }
            open.set(false);
            if (onClose != null) onClose.accept(closeFrame);
        } else if (closeState.compareAndSet(CloseState.CLOSING, CloseState.CLOSED)) {
            // We sent close first, now received the peer's close response
            if (code >= 0) {
                this.closeCode = code;
                this.closeReason = reason;
            }
            open.set(false);
            if (onClose != null) onClose.accept(closeFrame);
        }
    }

    /**
     * Initiates a close handshake with the default normal closure code.
     */
    public void close() {
        close(WebSocketCloseCode.NORMAL_CLOSURE.code(), WebSocketCloseCode.NORMAL_CLOSURE.reason());
    }

    /**
     * Initiates a close handshake with the given close frame.
     *
     * @param closeFrame the close frame to send
     */
    public void close(WebSocketFrame closeFrame) {
        int code = closeFrame.getCloseCode();
        String reason = closeFrame.getCloseReason();
        if (code >= 0) {
            close(code, reason);
        } else {
            close(WebSocketCloseCode.NORMAL_CLOSURE.code(), WebSocketCloseCode.NORMAL_CLOSURE.reason());
        }
    }

    /**
     * Initiates a close handshake with a specific status code and reason.
     *
     * @param code   the close status code (1000-4999)
     * @param reason the close reason string
     */
    public void close(int code, String reason) {
        if (closeState.compareAndSet(CloseState.OPEN, CloseState.CLOSING)) {
            this.closeCode = code;
            this.closeReason = reason != null ? reason : "";
            if (frameSender != null) {
                frameSender.accept(WebSocketFrame.close(code, reason));
            }
            // If no frame sender, transition directly to closed
            if (frameSender == null) {
                closeState.set(CloseState.CLOSED);
                open.set(false);
                if (onClose != null) {
                    onClose.accept(WebSocketFrame.close(code, reason));
                }
            }
        }
    }

    /**
     * Initiates a close handshake with a WebSocketCloseCode.
     *
     * @param closeCode the close code enum
     */
    public void close(WebSocketCloseCode closeCode) {
        close(closeCode.code(), closeCode.reason());
    }

    public void handleError(Throwable error) {
        if (onError != null) onError.accept(error);
    }
}
