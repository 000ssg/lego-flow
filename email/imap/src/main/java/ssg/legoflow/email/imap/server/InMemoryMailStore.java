package ssg.legoflow.email.imap.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * In-memory implementation of {@link MailStore}.
 *
 * <p>Provides a simple, thread-safe mail store with configurable users
 * and an INBOX created automatically for each authenticated user.
 * Suitable for testing and development.
 *
 * @since 0.1.0
 */
public final class InMemoryMailStore implements MailStore {

    private static final String DELIMITER = "/";

    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Map<String, Mailbox> mailboxes = new ConcurrentHashMap<>();
    private final AtomicLong uidValidityGen = new AtomicLong(System.currentTimeMillis() / 1000);

    /**
     * Creates an empty in-memory mail store.
     */
    public InMemoryMailStore() {
    }

    /**
     * Adds a user to the store. Creates an INBOX for the user.
     *
     * @param username the username
     * @param password the password
     * @return this store for chaining
     */
    public InMemoryMailStore addUser(String username, String password) {
        users.put(username, password);
        String inboxName = "INBOX";
        if (!mailboxes.containsKey(inboxName.toUpperCase())) {
            mailboxes.put(inboxName.toUpperCase(),
                    new Mailbox(inboxName, uidValidityGen.incrementAndGet()));
        }
        return this;
    }

    @Override
    public boolean authenticate(String username, String password) {
        String stored = users.get(username);
        return stored != null && stored.equals(password);
    }

    @Override
    public Mailbox getMailbox(String name) {
        return mailboxes.get(name.toUpperCase());
    }

    @Override
    public Mailbox createMailbox(String name) {
        String key = name.toUpperCase();
        if (mailboxes.containsKey(key)) {
            throw new IllegalArgumentException("Mailbox already exists: " + name);
        }
        Mailbox mailbox = new Mailbox(name, uidValidityGen.incrementAndGet());
        mailboxes.put(key, mailbox);
        return mailbox;
    }

    @Override
    public boolean deleteMailbox(String name) {
        String key = name.toUpperCase();
        if ("INBOX".equals(key)) {
            return false; // INBOX cannot be deleted per RFC
        }
        return mailboxes.remove(key) != null;
    }

    @Override
    public boolean renameMailbox(String oldName, String newName) {
        String oldKey = oldName.toUpperCase();
        String newKey = newName.toUpperCase();
        if ("INBOX".equals(oldKey)) {
            return false; // INBOX cannot be renamed per RFC
        }
        Mailbox mailbox = mailboxes.remove(oldKey);
        if (mailbox == null) return false;
        // Create a new mailbox with the new name but same content
        Mailbox renamed = new Mailbox(newName, mailbox.uidValidity());
        for (StoredMessage msg : mailbox.allMessages()) {
            renamed.append(msg.content(), msg.flags(), msg.internalDate());
        }
        mailboxes.put(newKey, renamed);
        return true;
    }

    @Override
    public List<String> listMailboxes(String reference, String pattern) {
        String fullPattern = (reference != null ? reference : "") + pattern;
        if (fullPattern.isEmpty()) {
            return List.of();
        }

        // Convert IMAP wildcards to regex
        String regex = fullPattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("%", "[^/]*");
        Pattern compiled = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

        List<String> result = new ArrayList<>();
        for (Mailbox mailbox : mailboxes.values()) {
            if (compiled.matcher(mailbox.name()).matches()) {
                result.add(mailbox.name());
            }
        }
        Collections.sort(result);
        return result;
    }

    @Override
    public String delimiter() {
        return DELIMITER;
    }

    /**
     * Returns all mailbox names in the store.
     *
     * @return unmodifiable set of names
     */
    public Set<String> mailboxNames() {
        Set<String> names = new TreeSet<>();
        for (Mailbox mb : mailboxes.values()) {
            names.add(mb.name());
        }
        return Collections.unmodifiableSet(names);
    }
}
