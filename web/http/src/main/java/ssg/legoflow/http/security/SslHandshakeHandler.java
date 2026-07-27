package ssg.legoflow.http.security;

public class SslHandshakeHandler {

    public enum HandshakeState {
        NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED
    }

    private HandshakeState state = HandshakeState.NOT_STARTED;
    private final SslConfig config;

    public SslHandshakeHandler(SslConfig config) {
        this.config = config;
    }

    public void beginHandshake() {
        state = HandshakeState.IN_PROGRESS;
    }

    public void completeHandshake() {
        state = HandshakeState.COMPLETED;
    }

    public void failHandshake() {
        state = HandshakeState.FAILED;
    }

    public HandshakeState getState() { return state; }
    public SslConfig getConfig() { return config; }
}
