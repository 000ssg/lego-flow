package ssg.legoflow.email.imap.client;

import ssg.legoflow.email.imap.protocol.ImapResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
/**
 * Manages IMAP IDLE mode for receiving push notifications.
 *
 * <p>Enters IDLE mode, waits for server push notifications, and
 * re-issues IDLE after the configured timeout or when a notification
 * is received. Runs on a virtual thread.
 *
 * @since 0.1.0
 */
public final class IdleManager implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(IdleManager.class);

    private final ImapConnection connection;
    private final Consumer<ImapResponse> notificationHandler;
    private final long idleTimeoutMillis;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = false;
    private volatile Future<?> idleFuture;

    /**
     * Creates an IDLE manager.
     *
     * @param connection          the IMAP connection
     * @param notificationHandler callback for server notifications
     * @param idleTimeoutMillis   how long to stay in IDLE before re-issuing (ms)
     */
    public IdleManager(ImapConnection connection, Consumer<ImapResponse> notificationHandler,
                       long idleTimeoutMillis) {
        this.connection = Objects.requireNonNull(connection);
        this.notificationHandler = Objects.requireNonNull(notificationHandler);
        this.idleTimeoutMillis = idleTimeoutMillis;
    }

    /**
     * Starts the IDLE loop.
     */
    public void start() {
        if (running) return;
        running = true;
        idleFuture = executor.submit(this::idleLoop);
    }

    /**
     * Stops the IDLE loop by sending DONE.
     */
    public void stop() {
        running = false;
        connection.sendLine("DONE");
        if (idleFuture != null) {
            idleFuture.cancel(true);
        }
    }

    private void idleLoop() {
        while (running && connection.isConnected()) {
            try {
                // Send IDLE command
                String tag = connection.nextTag();
                connection.sendLine(tag + " IDLE");

                // Read continuation response
                ImapResponse contResp = connection.readResponse();
                if (contResp == null || !contResp.isContinuation()) {
                    LOG.warn("Unexpected response to IDLE: {}", contResp);
                    break;
                }

                // Wait for notifications or timeout
                long start = System.currentTimeMillis();
                while (running && (System.currentTimeMillis() - start) < idleTimeoutMillis) {
                    ImapResponse response = connection.readResponse();
                    if (response == null) {
                        running = false;
                        break;
                    }
                    if (response.isTagged()) {
                        // IDLE completed
                        break;
                    }
                    if (response.isUntagged()) {
                        notificationHandler.accept(response);
                    }
                }

                // Send DONE to exit IDLE
                if (running) {
                    connection.sendLine("DONE");
                    // Read tagged OK
                    ImapResponse doneResp = connection.readResponse();
                    if (doneResp != null && doneResp.isTagged()) {
                        LOG.debug("IDLE cycle completed: {}", doneResp);
                    }
                }

            } catch (IOException e) {
                if (running) {
                    LOG.debug("IDLE interrupted: {}", e.getMessage());
                }
                running = false;
            }
        }
    }

    /** Returns true if the IDLE loop is running. */
    public boolean isRunning() { return running; }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }
}
