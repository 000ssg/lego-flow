package ssg.legoflow.email.smtp.dsn;

import ssg.legoflow.email.smtp.protocol.EnhancedStatusCode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates Delivery Status Notification messages per RFC 3464.
 *
 * <p>DSN messages are multipart/report MIME messages containing:
 * <ol>
 *   <li>Human-readable explanation (text/plain)</li>
 *   <li>Machine-readable delivery status (message/delivery-status)</li>
 *   <li>Original message or headers (message/rfc822 or text/rfc822-headers)</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DsnGenerator {

    private DsnGenerator() {
        // utility class
    }

    /**
     * Generates a bounce (failure) DSN message.
     *
     * @param reportingMta the reporting MTA hostname
     * @param originalFrom the original sender address
     * @param recipient    the failed recipient address
     * @param status       the enhanced status code
     * @param diagnostic   the diagnostic message
     * @param originalMsg  the original message (headers + body), or {@code null}
     * @return the complete DSN MIME message
     */
    public static String generateBounce(String reportingMta, String originalFrom,
                                        String recipient, EnhancedStatusCode status,
                                        String diagnostic, String originalMsg) {
        return generate(reportingMta, originalFrom, recipient,
                DeliveryStatus.Action.FAILED, status, diagnostic, originalMsg,
                DeliveryStatus.ReturnType.FULL);
    }

    /**
     * Generates a delay DSN message.
     *
     * @param reportingMta the reporting MTA hostname
     * @param originalFrom the original sender address
     * @param recipient    the delayed recipient address
     * @param diagnostic   the delay reason
     * @param originalMsg  the original message headers, or {@code null}
     * @return the complete DSN MIME message
     */
    public static String generateDelay(String reportingMta, String originalFrom,
                                       String recipient, String diagnostic,
                                       String originalMsg) {
        return generate(reportingMta, originalFrom, recipient,
                DeliveryStatus.Action.DELAYED,
                new EnhancedStatusCode(4, 0, 0), diagnostic, originalMsg,
                DeliveryStatus.ReturnType.HDRS);
    }

    /**
     * Generates a success DSN message (when NOTIFY=SUCCESS was requested).
     *
     * @param reportingMta the reporting MTA hostname
     * @param originalFrom the original sender address
     * @param recipient    the recipient address
     * @param originalMsg  the original message headers, or {@code null}
     * @return the complete DSN MIME message
     */
    public static String generateSuccess(String reportingMta, String originalFrom,
                                         String recipient, String originalMsg) {
        return generate(reportingMta, originalFrom, recipient,
                DeliveryStatus.Action.DELIVERED,
                EnhancedStatusCode.SUCCESS_OTHER, "Delivered successfully", originalMsg,
                DeliveryStatus.ReturnType.HDRS);
    }

    /**
     * Generates a complete DSN message.
     *
     * @param reportingMta the reporting MTA
     * @param originalFrom the original sender
     * @param recipient    the recipient
     * @param action       the delivery action
     * @param status       the status code
     * @param diagnostic   the diagnostic message
     * @param originalMsg  the original message (may be {@code null})
     * @param returnType   FULL or HDRS
     * @return the complete DSN MIME message
     */
    public static String generate(String reportingMta, String originalFrom,
                                  String recipient, DeliveryStatus.Action action,
                                  EnhancedStatusCode status, String diagnostic,
                                  String originalMsg, DeliveryStatus.ReturnType returnType) {
        String boundary = "DSN_" + UUID.randomUUID().toString().replace("-", "");
        String dateStr = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        var sb = new StringBuilder();

        // Message headers
        sb.append("From: Mail Delivery System <MAILER-DAEMON@").append(reportingMta).append(">\r\n");
        sb.append("To: <").append(originalFrom).append(">\r\n");
        sb.append("Subject: ").append(subjectForAction(action, recipient)).append("\r\n");
        sb.append("Date: ").append(dateStr).append("\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: multipart/report; report-type=delivery-status;\r\n");
        sb.append("    boundary=\"").append(boundary).append("\"\r\n");
        sb.append("\r\n");

        // Part 1: Human-readable explanation
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Type: text/plain; charset=utf-8\r\n");
        sb.append("\r\n");
        sb.append(humanReadable(action, recipient, diagnostic));
        sb.append("\r\n");

        // Part 2: Machine-readable delivery status
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Type: message/delivery-status\r\n");
        sb.append("\r\n");
        sb.append("Reporting-MTA: dns;").append(reportingMta).append("\r\n");
        sb.append("Arrival-Date: ").append(dateStr).append("\r\n");
        sb.append("\r\n");
        sb.append("Final-Recipient: rfc822;").append(recipient).append("\r\n");
        sb.append("Action: ").append(action.name().toLowerCase()).append("\r\n");
        sb.append("Status: ").append(status.wireForm()).append("\r\n");
        if (diagnostic != null) {
            sb.append("Diagnostic-Code: smtp;").append(diagnostic).append("\r\n");
        }
        sb.append("\r\n");

        // Part 3: Original message
        if (originalMsg != null) {
            sb.append("--").append(boundary).append("\r\n");
            if (returnType == DeliveryStatus.ReturnType.FULL) {
                sb.append("Content-Type: message/rfc822\r\n");
            } else {
                sb.append("Content-Type: text/rfc822-headers\r\n");
            }
            sb.append("\r\n");
            if (returnType == DeliveryStatus.ReturnType.HDRS) {
                // Extract just headers (up to first blank line)
                int headerEnd = originalMsg.indexOf("\r\n\r\n");
                if (headerEnd >= 0) {
                    sb.append(originalMsg, 0, headerEnd + 2);
                } else {
                    sb.append(originalMsg);
                }
            } else {
                sb.append(originalMsg);
            }
            sb.append("\r\n");
        }

        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }

    private static String subjectForAction(DeliveryStatus.Action action, String recipient) {
        return switch (action) {
            case FAILED -> "Undelivered Mail Returned to Sender";
            case DELAYED -> "Delivery Status Notification (Delay)";
            case DELIVERED -> "Delivery Status Notification (Delivered)";
            case RELAYED -> "Delivery Status Notification (Relayed)";
            case EXPANDED -> "Delivery Status Notification (Expanded)";
        };
    }

    private static String humanReadable(DeliveryStatus.Action action, String recipient,
                                        String diagnostic) {
        return switch (action) {
            case FAILED -> "This is the mail delivery system at the reporting MTA.\n\n"
                    + "I'm sorry to have to inform you that your message could not\n"
                    + "be delivered to one or more recipients.\n\n"
                    + "    <" + recipient + ">: " + (diagnostic != null ? diagnostic : "delivery failed") + "\n";
            case DELAYED -> "This is the mail delivery system at the reporting MTA.\n\n"
                    + "Your message has been delayed and is still being retried.\n\n"
                    + "    <" + recipient + ">: " + (diagnostic != null ? diagnostic : "delivery delayed") + "\n";
            case DELIVERED -> "This is the mail delivery system at the reporting MTA.\n\n"
                    + "Your message was successfully delivered.\n\n"
                    + "    <" + recipient + ">: delivered\n";
            default -> "Delivery status notification for <" + recipient + ">\n";
        };
    }
}
