package ssg.legoflow.email.imap.server;

import java.util.List;

/**
 * Interface for IMAP mail storage backends.
 *
 * <p>Provides mailbox management operations (create, delete, rename, list)
 * and authentication. Implementations must be thread-safe.
 *
 * @since 0.1.0
 */
public interface MailStore {

    /**
     * Authenticates a user.
     *
     * @param username the username
     * @param password the password
     * @return true if authentication succeeds
     */
    boolean authenticate(String username, String password);

    /**
     * Returns the mailbox with the given name.
     *
     * @param name the mailbox name
     * @return the mailbox, or null if not found
     */
    Mailbox getMailbox(String name);

    /**
     * Creates a new mailbox.
     *
     * @param name the mailbox name
     * @return the created mailbox
     * @throws IllegalArgumentException if the mailbox already exists
     */
    Mailbox createMailbox(String name);

    /**
     * Deletes a mailbox.
     *
     * @param name the mailbox name
     * @return true if deleted
     */
    boolean deleteMailbox(String name);

    /**
     * Renames a mailbox.
     *
     * @param oldName the current name
     * @param newName the new name
     * @return true if renamed
     */
    boolean renameMailbox(String oldName, String newName);

    /**
     * Lists mailboxes matching the given reference and pattern.
     *
     * @param reference the reference name (namespace prefix)
     * @param pattern   the mailbox pattern (supports '*' and '%' wildcards)
     * @return the list of matching mailbox names
     */
    List<String> listMailboxes(String reference, String pattern);

    /**
     * Returns the hierarchy delimiter character.
     *
     * @return the delimiter (e.g., "/")
     */
    String delimiter();
}
