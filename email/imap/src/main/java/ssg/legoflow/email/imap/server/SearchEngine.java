package ssg.legoflow.email.imap.server;

import ssg.legoflow.email.imap.protocol.SearchCriteria;
import ssg.legoflow.email.imap.protocol.SearchCriteria.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
/**
 * Evaluates IMAP SEARCH criteria against stored messages.
 *
 * <p>Supports all standard IMAP search criteria including flag tests,
 * header matching, date comparisons, size checks, body text search,
 * and composite AND/OR/NOT operations.
 *
 * @since 0.1.0
 */
public final class SearchEngine {

    private SearchEngine() {
    }

    /**
     * Searches a mailbox for messages matching the given criteria.
     *
     * @param mailbox  the mailbox to search
     * @param criteria the search criteria
     * @return the list of matching message sequence numbers (1-based)
     */
    public static List<Integer> search(Mailbox mailbox, SearchCriteria criteria) {
        List<Integer> result = new ArrayList<>();
        List<StoredMessage> messages = mailbox.allMessages();
        for (int i = 0; i < messages.size(); i++) {
            if (matches(messages.get(i), criteria)) {
                result.add(i + 1);
            }
        }
        return result;
    }

    /**
     * Searches a mailbox and returns matching UIDs.
     *
     * @param mailbox  the mailbox to search
     * @param criteria the search criteria
     * @return the list of matching UIDs
     */
    public static List<Long> searchUids(Mailbox mailbox, SearchCriteria criteria) {
        List<Long> result = new ArrayList<>();
        for (StoredMessage msg : mailbox.allMessages()) {
            if (matches(msg, criteria)) {
                result.add(msg.uid());
            }
        }
        return result;
    }

    /**
     * Tests whether a message matches the given criteria.
     *
     * @param msg      the message
     * @param criteria the criteria
     * @return true if the message matches
     */
    public static boolean matches(StoredMessage msg, SearchCriteria criteria) {
        return switch (criteria) {
            case All a -> true;

            case Flagged f -> matchFlag(msg, f.flag());

            case Header h -> {
                String value = msg.getHeader(h.field());
                yield value != null && value.toLowerCase().contains(h.value().toLowerCase());
            }

            case AddressField a -> {
                String fieldName = a.field().toLowerCase();
                String value = msg.getHeader(fieldName);
                yield value != null && value.toLowerCase().contains(a.value().toLowerCase());
            }

            case Subject s -> {
                String subject = msg.getHeader("Subject");
                yield subject != null && subject.toLowerCase().contains(s.value().toLowerCase());
            }

            case Body b -> msg.getBody().toLowerCase().contains(b.text().toLowerCase());

            case Text t -> msg.contentAsString().toLowerCase().contains(t.text().toLowerCase());

            case Before b -> dateCompare(msg.internalDate(), b.date()) < 0;
            case On o -> dateCompare(msg.internalDate(), o.date()) == 0;
            case Since s -> dateCompare(msg.internalDate(), s.date()) >= 0;

            case SentBefore sb -> {
                LocalDate sentDate = parseSentDate(msg);
                yield sentDate != null && sentDate.isBefore(sb.date());
            }
            case SentOn so -> {
                LocalDate sentDate = parseSentDate(msg);
                yield sentDate != null && sentDate.isEqual(so.date());
            }
            case SentSince ss -> {
                LocalDate sentDate = parseSentDate(msg);
                yield sentDate != null && !sentDate.isBefore(ss.date());
            }

            case Larger l -> msg.size() > l.size();
            case Smaller s -> msg.size() < s.size();

            case Keyword k -> msg.hasFlag(k.keyword());
            case Unkeyword u -> !msg.hasFlag(u.keyword());

            case Uid u -> matchUidSet(msg.uid(), u.uidSet());
            case SequenceSet s -> true; // sequence set matching handled at search caller level

            case ModSeq m -> msg.modSeq() >= m.modSeq();

            case And a -> a.criteria().stream().allMatch(c -> matches(msg, c));
            case Or o -> matches(msg, o.left()) || matches(msg, o.right());
            case Not n -> !matches(msg, n.criterion());
        };
    }

    private static boolean matchFlag(StoredMessage msg, String flag) {
        return switch (flag.toUpperCase()) {
            case "SEEN" -> msg.hasFlag("\\Seen");
            case "UNSEEN" -> !msg.hasFlag("\\Seen");
            case "ANSWERED" -> msg.hasFlag("\\Answered");
            case "UNANSWERED" -> !msg.hasFlag("\\Answered");
            case "FLAGGED" -> msg.hasFlag("\\Flagged");
            case "UNFLAGGED" -> !msg.hasFlag("\\Flagged");
            case "DELETED" -> msg.hasFlag("\\Deleted");
            case "UNDELETED" -> !msg.hasFlag("\\Deleted");
            case "DRAFT" -> msg.hasFlag("\\Draft");
            case "UNDRAFT" -> !msg.hasFlag("\\Draft");
            case "RECENT" -> msg.hasFlag("\\Recent");
            case "NEW" -> msg.hasFlag("\\Recent") && !msg.hasFlag("\\Seen");
            case "OLD" -> !msg.hasFlag("\\Recent");
            default -> msg.hasFlag(flag);
        };
    }

    private static int dateCompare(Instant messageDate, LocalDate targetDate) {
        LocalDate msgDate = messageDate.atOffset(ZoneOffset.UTC).toLocalDate();
        return msgDate.compareTo(targetDate);
    }

    private static LocalDate parseSentDate(StoredMessage msg) {
        String dateHeader = msg.getHeader("Date");
        if (dateHeader == null) return null;
        // Simplified date parsing -- handles common formats
        try {
            // Try to extract date part from RFC 5322 date
            String trimmed = dateHeader.trim();
            // Skip day of week if present
            int commaIdx = trimmed.indexOf(',');
            if (commaIdx >= 0) {
                trimmed = trimmed.substring(commaIdx + 1).trim();
            }
            // Parse "dd Mon yyyy" portion
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 3) {
                int day = Integer.parseInt(parts[0]);
                int month = parseMonth(parts[1]);
                int year = Integer.parseInt(parts[2]);
                if (month > 0) {
                    return LocalDate.of(year, month, day);
                }
            }
        } catch (Exception e) {
            // Fall through
        }
        return null;
    }

    private static int parseMonth(String month) {
        return switch (month.substring(0, 3).toLowerCase()) {
            case "jan" -> 1; case "feb" -> 2; case "mar" -> 3;
            case "apr" -> 4; case "may" -> 5; case "jun" -> 6;
            case "jul" -> 7; case "aug" -> 8; case "sep" -> 9;
            case "oct" -> 10; case "nov" -> 11; case "dec" -> 12;
            default -> -1;
        };
    }

    private static boolean matchUidSet(long uid, String uidSet) {
        String[] ranges = uidSet.split(",");
        for (String range : ranges) {
            range = range.trim();
            if (range.contains(":")) {
                String[] bounds = range.split(":");
                long start = "*".equals(bounds[0]) ? Long.MAX_VALUE : Long.parseLong(bounds[0]);
                long end = "*".equals(bounds[1]) ? Long.MAX_VALUE : Long.parseLong(bounds[1]);
                long lo = Math.min(start, end);
                long hi = Math.max(start, end);
                if (uid >= lo && uid <= hi) return true;
            } else {
                if ("*".equals(range) || Long.parseLong(range) == uid) return true;
            }
        }
        return false;
    }
}
