package ssg.legoflow.ssh.connection;

/**
 * SSH channel request types per RFC 4254 section 6.
 *
 * @param recipientChannel the channel to send the request on
 * @param requestType      the request type name
 * @param wantReply        whether a reply is expected
 * @param data             request-specific data
 * @since 1.0.0
 */
public record ChannelRequest(
        int recipientChannel,
        String requestType,
        boolean wantReply,
        byte[] data
) {

    /**
     * Well-known channel request types.
     */
    public static final String PTY_REQ = "pty-req";
    public static final String SHELL = "shell";
    public static final String EXEC = "exec";
    public static final String SUBSYSTEM = "subsystem";
    public static final String ENV = "env";
    public static final String SIGNAL = "signal";
    public static final String EXIT_STATUS = "exit-status";
    public static final String EXIT_SIGNAL = "exit-signal";
    public static final String WINDOW_CHANGE = "window-change";
    public static final String XON_XOFF = "xon-xoff";
    public static final String AUTH_AGENT_REQ = "auth-agent-req@openssh.com";
    public static final String X11_REQ = "x11-req";
    public static final String X11_FORWARDING = "x11";
}
