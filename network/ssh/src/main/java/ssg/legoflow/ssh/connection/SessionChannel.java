package ssg.legoflow.ssh.connection;

import ssg.legoflow.ssh.connection.X11ForwardingConfig;
import ssg.legoflow.ssh.transport.SshTransport;
import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Session channel for terminal, exec, and subsystem requests per RFC 4254 section 6.
 *
 * <p>Supports pty-req, shell, exec, subsystem, env, signal, exit-status, and exit-signal.
 *
 * @since 0.1.0
 */
public final class SessionChannel extends SshChannel {

    /**
     * Creates a new session channel.
     *
     * @param localId   the local channel ID
     * @param transport the transport layer
     */
    public SessionChannel(int localId, SshTransport transport) {
        super(localId, transport);
    }

    @Override
    public String channelType() { return "session"; }

    /**
     * Requests a pseudo-terminal.
     *
     * @param term   terminal type (e.g., "xterm-256color")
     * @param cols   number of columns
     * @param rows   number of rows
     * @param width  width in pixels
     * @param height height in pixels
     * @throws IOException if an I/O error occurs
     */
    public void requestPty(String term, int cols, int rows, int width, int height) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(256);
        SshTransportCodec.writeString(data, term);
        data.putInt(cols);
        data.putInt(rows);
        data.putInt(width);
        data.putInt(height);
        SshTransportCodec.writeString(data, ""); // encoded terminal modes
        data.flip();
        byte[] dataBytes = new byte[data.remaining()];
        data.get(dataBytes);
        sendChannelRequest("pty-req", true, dataBytes);
    }

    /**
     * Requests a shell.
     *
     * @throws IOException if an I/O error occurs
     */
    public void requestShell() throws IOException {
        sendChannelRequest("shell", true, new byte[0]);
    }

    /**
     * Requests execution of a command.
     *
     * @param command the command to execute
     * @throws IOException if an I/O error occurs
     */
    public void requestExec(String command) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(4 + command.length());
        SshTransportCodec.writeString(data, command);
        data.flip();
        byte[] dataBytes = new byte[data.remaining()];
        data.get(dataBytes);
        sendChannelRequest("exec", true, dataBytes);
    }

    /**
     * Requests a subsystem (e.g., "sftp").
     *
     * @param subsystem the subsystem name
     * @throws IOException if an I/O error occurs
     */
    public void requestSubsystem(String subsystem) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(4 + subsystem.length());
        SshTransportCodec.writeString(data, subsystem);
        data.flip();
        byte[] dataBytes = new byte[data.remaining()];
        data.get(dataBytes);
        sendChannelRequest("subsystem", true, dataBytes);
    }

    /**
     * Sets an environment variable.
     *
     * @param name  the variable name
     * @param value the variable value
     * @throws IOException if an I/O error occurs
     */
    public void setEnv(String name, String value) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(8 + name.length() + value.length());
        SshTransportCodec.writeString(data, name);
        SshTransportCodec.writeString(data, value);
        data.flip();
        byte[] dataBytes = new byte[data.remaining()];
        data.get(dataBytes);
        sendChannelRequest("env", false, dataBytes);
    }

    /**
     * Sends a signal to the remote process.
     *
     * @param signal the signal name (e.g., "TERM", "KILL")
     * @throws IOException if an I/O error occurs
     */
    public void sendSignal(String signal) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(4 + signal.length());
        SshTransportCodec.writeString(data, signal);
        data.flip();
        byte[] dataBytes = new byte[data.remaining()];
        data.get(dataBytes);
        sendChannelRequest("signal", false, dataBytes);
    }

    /**
     * Requests agent forwarding on this session.
     *
     * <p>Sends a {@code "auth-agent-req@openssh.com"} channel request to enable
     * SSH agent forwarding per RFC 4254 section 6.3.
     *
     * @throws IOException if an I/O error occurs
     */
    public void requestAgentForwarding() throws IOException {
        sendChannelRequest(ChannelRequest.AUTH_AGENT_REQ, true, new byte[0]);
    }

    /**
     * Requests X11 forwarding on this session.
     *
     * <p>Sends an {@code "x11-req"} channel request per RFC 4254 section 6.3.1.
     *
     * @param config the X11 forwarding configuration
     * @throws IOException if an I/O error occurs
     */
    public void requestX11Forwarding(X11ForwardingConfig config) throws IOException {
        String hexCookie = bytesToHex(config.authCookie());
        ByteBuffer data = ByteBuffer.allocate(256);
        SshTransportCodec.writeBoolean(data, config.singleConnection());
        SshTransportCodec.writeString(data, config.authProtocol());
        SshTransportCodec.writeString(data, hexCookie);
        data.putInt(config.screenNumber());
        data.flip();
        byte[] dataBytes = new byte[data.remaining()];
        data.get(dataBytes);
        sendChannelRequest(ChannelRequest.X11_REQ, true, dataBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Sends a channel request and optionally waits for the reply.
     */
    private void sendChannelRequest(String requestType, boolean wantReply, byte[] data)
            throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(64 + requestType.length() + data.length);
        buf.put((byte) 98); // SSH_MSG_CHANNEL_REQUEST
        buf.putInt(remoteId());
        SshTransportCodec.writeString(buf, requestType);
        SshTransportCodec.writeBoolean(buf, wantReply);
        buf.put(data);
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        transport().sendPacket(payload);

        if (wantReply) {
            try {
                waitForRequestReply(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for channel request reply", e);
            }
        }
    }
}
