package ssg.legoflow.messaging.stomp.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicLong;
/**
 * Heart-beat negotiation and monitoring per STOMP 1.2 specification.
 *
 * <p>Heart-beat header format: {@code cx,cy} where:
 * <ul>
 *   <li>{@code cx} — smallest number of milliseconds between heart-beats the sender
 *       can do (0 means it cannot send heart-beats)</li>
 *   <li>{@code cy} — smallest number of milliseconds between heart-beats the sender
 *       would like to receive (0 means it does not want to receive heart-beats)</li>
 * </ul>
 *
 * <p>The negotiated intervals are:
 * <ul>
 *   <li>Send interval = MAX(client-cx, server-cy) if both non-zero, else 0</li>
 *   <li>Receive interval = MAX(server-cx, client-cy) if both non-zero, else 0</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class HeartbeatMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final AtomicLong lastSendTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastReceiveTime = new AtomicLong(System.currentTimeMillis());

    private volatile int sendInterval;
    private volatile int receiveInterval;
    private volatile boolean active;

    /**
     * Parses a heart-beat header value into a two-element array [cx, cy].
     *
     * @param heartBeatHeader the header value (e.g. "10000,10000")
     * @return array of [sendInterval, receiveInterval] in milliseconds
     * @throws StompProtocolException if the format is invalid
     */
    public static int[] parseHeartbeat(String heartBeatHeader) {
        if (heartBeatHeader == null || heartBeatHeader.isBlank()) {
            return new int[]{0, 0};
        }
        String[] parts = heartBeatHeader.trim().split(",");
        if (parts.length != 2) {
            throw new StompProtocolException("Invalid heart-beat format: " + heartBeatHeader);
        }
        try {
            int cx = Integer.parseInt(parts[0].trim());
            int cy = Integer.parseInt(parts[1].trim());
            if (cx < 0 || cy < 0) {
                throw new StompProtocolException("Heart-beat values must be non-negative: " + heartBeatHeader);
            }
            return new int[]{cx, cy};
        } catch (NumberFormatException e) {
            throw new StompProtocolException("Invalid heart-beat values: " + heartBeatHeader, e);
        }
    }

    /**
     * Formats heart-beat values into a header value string.
     *
     * @param cx send interval in milliseconds
     * @param cy receive interval in milliseconds
     * @return the formatted header value
     */
    public static String formatHeartbeat(int cx, int cy) {
        return cx + "," + cy;
    }

    /**
     * Negotiates heart-beat intervals between client and server.
     *
     * @param clientCx client's send capability (ms)
     * @param clientCy client's receive desire (ms)
     * @param serverCx server's send capability (ms)
     * @param serverCy server's receive desire (ms)
     * @return array of [negotiatedSend, negotiatedReceive] from the perspective of
     *         whoever calls this (client: send to server, receive from server)
     */
    public static int[] negotiate(int clientCx, int clientCy, int serverCx, int serverCy) {
        // Client sends at: MAX(client-cx, server-cy) if both non-zero
        int sendInterval = (clientCx == 0 || serverCy == 0) ? 0 : Math.max(clientCx, serverCy);
        // Client receives at: MAX(server-cx, client-cy) if both non-zero
        int receiveInterval = (serverCx == 0 || clientCy == 0) ? 0 : Math.max(serverCx, clientCy);
        return new int[]{sendInterval, receiveInterval};
    }

    /**
     * Configures and activates heart-beat monitoring.
     *
     * @param sendInterval    negotiated send interval (ms), 0 = disabled
     * @param receiveInterval negotiated receive interval (ms), 0 = disabled
     */
    public void start(int sendInterval, int receiveInterval) {
        this.sendInterval = sendInterval;
        this.receiveInterval = receiveInterval;
        this.active = sendInterval > 0 || receiveInterval > 0;
        long now = System.currentTimeMillis();
        lastSendTime.set(now);
        lastReceiveTime.set(now);
        LOG.debug("Heart-beat started: send={}ms, receive={}ms", sendInterval, receiveInterval);
    }

    /**
     * Stops heart-beat monitoring.
     */
    public void stop() {
        this.active = false;
        LOG.debug("Heart-beat stopped");
    }

    /**
     * Records that data was sent (resets the send timer).
     */
    public void markSent() {
        lastSendTime.set(System.currentTimeMillis());
    }

    /**
     * Records that data was received (resets the receive timer).
     */
    public void markReceived() {
        lastReceiveTime.set(System.currentTimeMillis());
    }

    /**
     * Returns whether a heart-beat should be sent now (enough time has elapsed
     * since the last send).
     *
     * @return {@code true} if a heart-beat should be sent
     */
    public boolean shouldSendHeartbeat() {
        if (!active || sendInterval == 0) return false;
        return System.currentTimeMillis() - lastSendTime.get() >= sendInterval;
    }

    /**
     * Returns whether the receive timeout has been exceeded, indicating the
     * remote end may have died.
     *
     * <p>Per STOMP spec, a tolerance margin is applied (typically 2x the interval).
     *
     * @return {@code true} if receive timeout exceeded
     */
    public boolean isReceiveTimedOut() {
        if (!active || receiveInterval == 0) return false;
        // Allow a generous margin of 2x the interval before declaring timeout
        long tolerance = receiveInterval * 2L;
        return System.currentTimeMillis() - lastReceiveTime.get() > tolerance;
    }

    /**
     * Returns the negotiated send interval.
     *
     * @return send interval in milliseconds, 0 if disabled
     */
    public int getSendInterval() {
        return sendInterval;
    }

    /**
     * Returns the negotiated receive interval.
     *
     * @return receive interval in milliseconds, 0 if disabled
     */
    public int getReceiveInterval() {
        return receiveInterval;
    }

    /**
     * Returns whether heart-beat monitoring is active.
     *
     * @return {@code true} if active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns the milliseconds since the last send.
     *
     * @return ms since last send
     */
    public long timeSinceLastSend() {
        return System.currentTimeMillis() - lastSendTime.get();
    }

    /**
     * Returns the milliseconds since the last receive.
     *
     * @return ms since last receive
     */
    public long timeSinceLastReceive() {
        return System.currentTimeMillis() - lastReceiveTime.get();
    }
}
