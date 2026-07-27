package ssg.legoflow.email.smtp.dsn;

import ssg.legoflow.email.smtp.protocol.EnhancedStatusCode;

import java.time.Instant;
import java.util.*;

/**
 * Represents a Delivery Status Notification per RFC 3461 and RFC 3464.
 *
 * <p>A DSN report contains:
 * <ul>
 *   <li>Per-message fields: reporting MTA, envelope ID, arrival date</li>
 *   <li>Per-recipient fields: status, action, diagnostic code, remote MTA</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class DeliveryStatus {

    /** DSN action types. */
    public enum Action {
        /** Message delivered successfully. */
        DELIVERED,
        /** Message could not be delivered (permanent failure). */
        FAILED,
        /** Message delivery was delayed (will retry). */
        DELAYED,
        /** Message was relayed to a non-DSN-aware MTA. */
        RELAYED,
        /** Message was expanded from a mailing list. */
        EXPANDED
    }

    /** DSN notification types for RCPT TO NOTIFY parameter. */
    public enum NotifyType {
        /** Notify on successful delivery. */
        SUCCESS,
        /** Notify on delivery failure. */
        FAILURE,
        /** Notify on delivery delay. */
        DELAY,
        /** Never send DSN. */
        NEVER
    }

    /** DSN RET parameter values for MAIL FROM. */
    public enum ReturnType {
        /** Return full message in DSN. */
        FULL,
        /** Return headers only in DSN. */
        HDRS
    }

    private final String reportingMta;
    private final String envelopeId;
    private final Instant arrivalDate;
    private final List<RecipientStatus> recipientStatuses;

    /**
     * Creates a delivery status report.
     *
     * @param reportingMta      the reporting MTA
     * @param envelopeId        the envelope ID (from ENVID parameter)
     * @param arrivalDate       the message arrival date
     * @param recipientStatuses the per-recipient status reports
     */
    public DeliveryStatus(String reportingMta, String envelopeId, Instant arrivalDate,
                          List<RecipientStatus> recipientStatuses) {
        this.reportingMta = Objects.requireNonNull(reportingMta, "reportingMta");
        this.envelopeId = envelopeId;
        this.arrivalDate = arrivalDate != null ? arrivalDate : Instant.now();
        this.recipientStatuses = Collections.unmodifiableList(new ArrayList<>(recipientStatuses));
    }

    /** Returns the reporting MTA. */
    public String reportingMta() { return reportingMta; }
    /** Returns the envelope ID, if any. */
    public String envelopeId() { return envelopeId; }
    /** Returns the arrival date. */
    public Instant arrivalDate() { return arrivalDate; }
    /** Returns the per-recipient statuses. */
    public List<RecipientStatus> recipientStatuses() { return recipientStatuses; }

    /**
     * Parses a DSN report from its text representation.
     *
     * @param text the DSN report text
     * @return the parsed delivery status
     */
    public static DeliveryStatus parse(String text) {
        String reportingMta = "";
        String envelopeId = null;
        Instant arrivalDate = null;
        var recipients = new ArrayList<RecipientStatus>();

        // Parse per-message fields (before the first blank line)
        String[] sections = text.split("\r?\n\r?\n");
        if (sections.length > 0) {
            var fields = parseFields(sections[0]);
            reportingMta = fields.getOrDefault("Reporting-MTA", "");
            if (reportingMta.startsWith("dns;")) {
                reportingMta = reportingMta.substring(4).trim();
            }
            envelopeId = fields.get("Original-Envelope-Id");
            String dateStr = fields.get("Arrival-Date");
            if (dateStr != null) {
                try {
                    arrivalDate = Instant.parse(dateStr);
                } catch (Exception e) {
                    arrivalDate = Instant.now();
                }
            }
        }

        // Parse per-recipient sections
        for (int i = 1; i < sections.length; i++) {
            String section = sections[i].trim();
            if (section.isEmpty()) continue;
            var fields = parseFields(section);

            String finalRecipient = fields.getOrDefault("Final-Recipient", "");
            if (finalRecipient.startsWith("rfc822;")) {
                finalRecipient = finalRecipient.substring(7).trim();
            }
            String originalRecipient = fields.get("Original-Recipient");
            if (originalRecipient != null && originalRecipient.startsWith("rfc822;")) {
                originalRecipient = originalRecipient.substring(7).trim();
            }

            String statusStr = fields.getOrDefault("Status", "5.0.0");
            EnhancedStatusCode status;
            try {
                status = EnhancedStatusCode.parse(statusStr);
            } catch (IllegalArgumentException e) {
                status = new EnhancedStatusCode(5, 0, 0);
            }

            String actionStr = fields.getOrDefault("Action", "failed");
            Action action = switch (actionStr.toLowerCase()) {
                case "delivered" -> Action.DELIVERED;
                case "delayed" -> Action.DELAYED;
                case "relayed" -> Action.RELAYED;
                case "expanded" -> Action.EXPANDED;
                default -> Action.FAILED;
            };

            String diagnosticCode = fields.get("Diagnostic-Code");
            String remoteMta = fields.get("Remote-MTA");
            if (remoteMta != null && remoteMta.startsWith("dns;")) {
                remoteMta = remoteMta.substring(4).trim();
            }

            recipients.add(new RecipientStatus(
                    finalRecipient, originalRecipient, action, status,
                    diagnosticCode, remoteMta));
        }

        return new DeliveryStatus(reportingMta, envelopeId, arrivalDate, recipients);
    }

    /**
     * Formats this DSN as text.
     *
     * @return the DSN text representation
     */
    public String format() {
        var sb = new StringBuilder();
        sb.append("Reporting-MTA: dns;").append(reportingMta).append("\r\n");
        if (envelopeId != null) {
            sb.append("Original-Envelope-Id: ").append(envelopeId).append("\r\n");
        }
        sb.append("Arrival-Date: ").append(arrivalDate).append("\r\n");

        for (RecipientStatus rs : recipientStatuses) {
            sb.append("\r\n");
            sb.append("Final-Recipient: rfc822;").append(rs.finalRecipient()).append("\r\n");
            if (rs.originalRecipient() != null) {
                sb.append("Original-Recipient: rfc822;").append(rs.originalRecipient()).append("\r\n");
            }
            sb.append("Action: ").append(rs.action().name().toLowerCase()).append("\r\n");
            sb.append("Status: ").append(rs.status().wireForm()).append("\r\n");
            if (rs.diagnosticCode() != null) {
                sb.append("Diagnostic-Code: ").append(rs.diagnosticCode()).append("\r\n");
            }
            if (rs.remoteMta() != null) {
                sb.append("Remote-MTA: dns;").append(rs.remoteMta()).append("\r\n");
            }
        }
        return sb.toString();
    }

    private static Map<String, String> parseFields(String section) {
        var fields = new LinkedHashMap<String, String>();
        for (String line : section.split("\r?\n")) {
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                fields.put(key, value);
            }
        }
        return fields;
    }

    /**
     * Per-recipient delivery status.
     *
     * @param finalRecipient    the final recipient address
     * @param originalRecipient the original recipient address (may be {@code null})
     * @param action            the delivery action
     * @param status            the enhanced status code
     * @param diagnosticCode    the diagnostic code (may be {@code null})
     * @param remoteMta         the remote MTA (may be {@code null})
     */
    public record RecipientStatus(
            String finalRecipient,
            String originalRecipient,
            Action action,
            EnhancedStatusCode status,
            String diagnosticCode,
            String remoteMta
    ) {}
}
