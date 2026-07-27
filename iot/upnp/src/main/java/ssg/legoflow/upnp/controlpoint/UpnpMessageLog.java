package ssg.legoflow.upnp.controlpoint;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Captures UPnP protocol messages (SSDP, SOAP, GENA, HTTP descriptions)
 * for diagnostic purposes.
 *
 * <p>Logging is disabled by default and must be enabled via {@link #setEnabled(boolean)}.
 * When enabled, all protocol messages are captured with timestamps and can be
 * retrieved for display in the UI or export for bug reports.
 *
 * <p>Thread-safe: messages can be logged from any thread (virtual threads
 * handling SSDP, SOAP responses, etc.) and read from the EDT.
 *
 * @since 1.0.0
 */
public final class UpnpMessageLog {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final int MAX_ENTRIES = 2000;

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<LogEntry> entries = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<LogEntry>> listeners = new CopyOnWriteArrayList<>();

    /**
     * A single logged protocol message.
     *
     * @param timestamp when the message was captured
     * @param direction {@code ">>>"} for outgoing, {@code "<<<"} for incoming
     * @param protocol  the protocol layer (SSDP, SOAP, GENA, HTTP)
     * @param summary   a one-line summary (e.g. "M-SEARCH *", "Browse → 192.168.1.50")
     * @param body      the full message body (XML, HTTP headers, etc.), may be {@code null}
     * @since 1.0.0
     */
    public record LogEntry(Instant timestamp, String direction, String protocol,
                            String summary, String body) {

        /**
         * Formats this entry as a human-readable string for display.
         *
         * @return formatted log line
         */
        public String format() {
            var sb = new StringBuilder();
            sb.append('[').append(TIME_FMT.format(timestamp)).append("] ");
            sb.append(direction).append(' ').append(protocol).append(": ");
            sb.append(summary);
            if (body != null && !body.isEmpty()) {
                sb.append('\n').append(body);
            }
            return sb.toString();
        }
    }

    /**
     * Creates a new message log (disabled by default).
     *
     * @since 1.0.0
     */
    public UpnpMessageLog() {
    }

    /**
     * Enables or disables message logging.
     *
     * @param enabled {@code true} to start capturing, {@code false} to stop
     * @since 1.0.0
     */
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    /**
     * Returns whether logging is currently enabled.
     *
     * @return {@code true} if enabled
     * @since 1.0.0
     */
    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Logs an outgoing message.
     *
     * @param protocol the protocol layer
     * @param summary  one-line summary
     * @param body     the message body, or {@code null}
     * @since 1.0.0
     */
    public void logOutgoing(String protocol, String summary, String body) {
        log(">>>", protocol, summary, body);
    }

    /**
     * Logs an incoming message.
     *
     * @param protocol the protocol layer
     * @param summary  one-line summary
     * @param body     the message body, or {@code null}
     * @since 1.0.0
     */
    public void logIncoming(String protocol, String summary, String body) {
        log("<<<", protocol, summary, body);
    }

    private void log(String direction, String protocol, String summary, String body) {
        if (!enabled.get()) return;

        var entry = new LogEntry(Instant.now(), direction, protocol, summary, body);
        entries.add(entry);

        // Trim old entries if too many
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }

        // Notify listeners
        for (var listener : listeners) {
            try {
                listener.accept(entry);
            } catch (Exception ignored) {
                // Don't let a bad listener break logging
            }
        }
    }

    /**
     * Returns all captured log entries.
     *
     * @return an unmodifiable snapshot of log entries
     * @since 1.0.0
     */
    public List<LogEntry> getEntries() {
        return List.copyOf(entries);
    }

    /**
     * Clears all captured log entries.
     *
     * @since 1.0.0
     */
    public void clear() {
        entries.clear();
    }

    /**
     * Adds a listener notified when a new log entry is captured.
     *
     * @param listener the listener
     * @since 1.0.0
     */
    public void addListener(Consumer<LogEntry> listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     * @since 1.0.0
     */
    public void removeListener(Consumer<LogEntry> listener) {
        listeners.remove(listener);
    }

    /**
     * Formats all entries as a single string for export/display.
     *
     * @return all entries formatted, separated by blank lines
     * @since 1.0.0
     */
    public String exportAll() {
        var sb = new StringBuilder();
        for (var entry : entries) {
            sb.append(entry.format()).append("\n\n");
        }
        return sb.toString();
    }
}
