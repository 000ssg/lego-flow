package ssg.legoflow.network.syslog.protocol;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
/**
 * RFC 5424 syslog message.
 *
 * <p>Format: {@code <PRI>VERSION TIMESTAMP HOSTNAME APP-NAME PROCID MSGID [SD-ELEMENT...] MSG}
 *
 * <p>PRI = facility * 8 + severity. VERSION is always 1 for RFC 5424.
 *
 * @param facility       the facility that generated the message
 * @param severity       the severity level
 * @param timestamp      the message timestamp (null for NILVALUE "-")
 * @param hostname       the hostname (null for NILVALUE "-")
 * @param appName        the application name (null for NILVALUE "-")
 * @param procId         the process ID (null for NILVALUE "-")
 * @param msgId          the message ID (null for NILVALUE "-")
 * @param structuredData the structured data elements (empty list for NILVALUE "-")
 * @param message        the free-form message text (null if absent)
 * @since 0.1.0
 */
public record SyslogMessage(
        Facility facility,
        Severity severity,
        Instant timestamp,
        String hostname,
        String appName,
        String procId,
        String msgId,
        List<StructuredData> structuredData,
        String message
) {

    /** RFC 5424 version number. */
    public static final int VERSION = 1;

    /** Maximum length for hostname, appName, procId, msgId per RFC 5424. */
    private static final int MAX_HOSTNAME = 255;
    private static final int MAX_APP_NAME = 48;
    private static final int MAX_PROC_ID = 128;
    private static final int MAX_MSG_ID = 32;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX");

    /**
     * Creates a syslog message with validation.
     */
    public SyslogMessage {
        Objects.requireNonNull(facility, "Facility must not be null");
        Objects.requireNonNull(severity, "Severity must not be null");
        structuredData = structuredData != null ? List.copyOf(structuredData) : List.of();
        if (hostname != null && hostname.length() > MAX_HOSTNAME) {
            throw new IllegalArgumentException("Hostname exceeds max length " + MAX_HOSTNAME);
        }
        if (appName != null && appName.length() > MAX_APP_NAME) {
            throw new IllegalArgumentException("App name exceeds max length " + MAX_APP_NAME);
        }
        if (procId != null && procId.length() > MAX_PROC_ID) {
            throw new IllegalArgumentException("ProcID exceeds max length " + MAX_PROC_ID);
        }
        if (msgId != null && msgId.length() > MAX_MSG_ID) {
            throw new IllegalArgumentException("MsgID exceeds max length " + MAX_MSG_ID);
        }
    }

    /**
     * Computes the PRI value (facility * 8 + severity).
     *
     * @return the PRI value
     */
    public int pri() {
        return facility.code() * 8 + severity.code();
    }

    /**
     * Returns a new builder for constructing syslog messages.
     *
     * @param facility the facility
     * @param severity the severity
     * @return a new builder
     */
    public static Builder builder(Facility facility, Severity severity) {
        return new Builder(facility, severity);
    }

    /**
     * Builder for constructing syslog messages.
     */
    public static final class Builder {
        private final Facility facility;
        private final Severity severity;
        private Instant timestamp;
        private String hostname;
        private String appName;
        private String procId;
        private String msgId;
        private List<StructuredData> structuredData = List.of();
        private String message;

        private Builder(Facility facility, Severity severity) {
            this.facility = facility;
            this.severity = severity;
        }

        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder hostname(String hostname) { this.hostname = hostname; return this; }
        public Builder appName(String appName) { this.appName = appName; return this; }
        public Builder procId(String procId) { this.procId = procId; return this; }
        public Builder msgId(String msgId) { this.msgId = msgId; return this; }
        public Builder structuredData(List<StructuredData> sd) { this.structuredData = sd; return this; }
        public Builder message(String message) { this.message = message; return this; }

        /**
         * Builds the syslog message.
         *
         * @return the constructed message
         */
        public SyslogMessage build() {
            return new SyslogMessage(facility, severity, timestamp, hostname,
                    appName, procId, msgId, structuredData, message);
        }
    }
}
