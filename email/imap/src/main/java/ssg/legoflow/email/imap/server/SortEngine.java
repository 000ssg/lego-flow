package ssg.legoflow.email.imap.server;

import ssg.legoflow.email.imap.protocol.SearchCriteria;
import ssg.legoflow.email.imap.protocol.SortCriteria;
import ssg.legoflow.email.imap.protocol.SortCriteria.SortKey;
import ssg.legoflow.email.imap.protocol.SortCriteria.ThreadAlgorithm;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side SORT and THREAD engine per RFC 5256.
 *
 * <p>Sorts messages by specified criteria and threads messages
 * using ORDEREDSUBJECT or REFERENCES algorithms.
 *
 * @since 0.1.0
 */
public final class SortEngine {

    private SortEngine() {
    }

    /**
     * Sorts messages in a mailbox by the given criteria, filtered by search criteria.
     *
     * @param mailbox        the mailbox
     * @param sortCriteria   the sort criteria (first has highest priority)
     * @param searchCriteria the search filter (null for ALL)
     * @return the sorted list of UIDs
     */
    public static List<Long> sort(Mailbox mailbox, List<SortCriteria> sortCriteria,
                                  SearchCriteria searchCriteria) {
        List<StoredMessage> messages = mailbox.allMessages();

        // Filter by search criteria
        if (searchCriteria != null) {
            messages = messages.stream()
                    .filter(m -> SearchEngine.matches(m, searchCriteria))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        // Build comparator chain
        Comparator<StoredMessage> comparator = buildComparator(sortCriteria);
        messages.sort(comparator);

        return messages.stream().map(StoredMessage::uid).toList();
    }

    /**
     * Threads messages using the specified algorithm.
     *
     * @param mailbox        the mailbox
     * @param algorithm      the threading algorithm
     * @param searchCriteria the search filter (null for ALL)
     * @return the thread structure as a list of thread roots, where each root
     *         contains [uid, child1, child2, ...] represented as nested lists
     */
    public static List<List<Long>> thread(Mailbox mailbox, ThreadAlgorithm algorithm,
                                          SearchCriteria searchCriteria) {
        List<StoredMessage> messages = mailbox.allMessages();

        if (searchCriteria != null) {
            messages = messages.stream()
                    .filter(m -> SearchEngine.matches(m, searchCriteria))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        return switch (algorithm) {
            case ORDEREDSUBJECT -> threadByOrderedSubject(messages);
            case REFERENCES -> threadByReferences(messages);
        };
    }

    private static Comparator<StoredMessage> buildComparator(List<SortCriteria> criteria) {
        Comparator<StoredMessage> result = null;
        for (SortCriteria sc : criteria) {
            Comparator<StoredMessage> comp = comparatorForKey(sc.key());
            if (sc.reverse()) {
                comp = comp.reversed();
            }
            result = result == null ? comp : result.thenComparing(comp);
        }
        return result != null ? result : Comparator.comparingLong(StoredMessage::uid);
    }

    private static Comparator<StoredMessage> comparatorForKey(SortKey key) {
        return switch (key) {
            case ARRIVAL -> Comparator.comparing(StoredMessage::internalDate);
            case DATE -> Comparator.comparing(m -> {
                String date = m.getHeader("Date");
                return date != null ? date : "";
            });
            case FROM -> Comparator.comparing(m -> {
                String from = m.getHeader("From");
                return from != null ? from.toLowerCase() : "";
            });
            case TO -> Comparator.comparing(m -> {
                String to = m.getHeader("To");
                return to != null ? to.toLowerCase() : "";
            });
            case CC -> Comparator.comparing(m -> {
                String cc = m.getHeader("Cc");
                return cc != null ? cc.toLowerCase() : "";
            });
            case SUBJECT -> Comparator.comparing(m -> baseSubject(m.getHeader("Subject")));
            case SIZE -> Comparator.comparingInt(StoredMessage::size);
        };
    }

    /**
     * Strips "Re:", "Fwd:", and similar prefixes from a subject for threading/sorting.
     */
    static String baseSubject(String subject) {
        if (subject == null) return "";
        String s = subject.trim();
        while (true) {
            String lower = s.toLowerCase();
            if (lower.startsWith("re:") || lower.startsWith("fw:")) {
                s = s.substring(3).trim();
            } else if (lower.startsWith("fwd:")) {
                s = s.substring(4).trim();
            } else if (s.startsWith("[") && s.contains("]")) {
                // Strip mailing list tags like [list]
                s = s.substring(s.indexOf(']') + 1).trim();
            } else {
                break;
            }
        }
        return s.toLowerCase();
    }

    private static List<List<Long>> threadByOrderedSubject(List<StoredMessage> messages) {
        // Group by base subject, sort each group by date
        Map<String, List<StoredMessage>> groups = new LinkedHashMap<>();
        for (StoredMessage msg : messages) {
            String base = baseSubject(msg.getHeader("Subject"));
            groups.computeIfAbsent(base, k -> new ArrayList<>()).add(msg);
        }

        List<List<Long>> threads = new ArrayList<>();
        for (List<StoredMessage> group : groups.values()) {
            group.sort(Comparator.comparing(StoredMessage::internalDate));
            List<Long> thread = group.stream().map(StoredMessage::uid).collect(Collectors.toList());
            threads.add(thread);
        }
        return threads;
    }

    private static List<List<Long>> threadByReferences(List<StoredMessage> messages) {
        // Build thread tree using References and In-Reply-To headers
        Map<String, StoredMessage> byMessageId = new LinkedHashMap<>();
        Map<String, List<String>> childMap = new LinkedHashMap<>();

        for (StoredMessage msg : messages) {
            String msgId = msg.getHeader("Message-ID");
            if (msgId != null) {
                byMessageId.put(msgId.trim(), msg);
            }
        }

        Set<String> hasParent = new HashSet<>();
        for (StoredMessage msg : messages) {
            String msgId = msg.getHeader("Message-ID");
            String inReplyTo = msg.getHeader("In-Reply-To");
            String references = msg.getHeader("References");

            String parentId = null;
            if (inReplyTo != null && !inReplyTo.isBlank()) {
                parentId = inReplyTo.trim();
            } else if (references != null && !references.isBlank()) {
                String[] refs = references.trim().split("\\s+");
                if (refs.length > 0) {
                    parentId = refs[refs.length - 1];
                }
            }

            if (parentId != null && msgId != null) {
                childMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(msgId.trim());
                hasParent.add(msgId.trim());
            }
        }

        // Find root messages (no parent)
        List<List<Long>> threads = new ArrayList<>();
        for (StoredMessage msg : messages) {
            String msgId = msg.getHeader("Message-ID");
            if (msgId == null || !hasParent.contains(msgId.trim())) {
                List<Long> thread = new ArrayList<>();
                thread.add(msg.uid());
                if (msgId != null) {
                    collectChildren(msgId.trim(), childMap, byMessageId, thread);
                }
                threads.add(thread);
            }
        }
        return threads;
    }

    private static void collectChildren(String parentId, Map<String, List<String>> childMap,
                                        Map<String, StoredMessage> byMessageId, List<Long> thread) {
        List<String> children = childMap.get(parentId);
        if (children != null) {
            for (String childId : children) {
                StoredMessage child = byMessageId.get(childId);
                if (child != null) {
                    thread.add(child.uid());
                    collectChildren(childId, childMap, byMessageId, thread);
                }
            }
        }
    }
}
