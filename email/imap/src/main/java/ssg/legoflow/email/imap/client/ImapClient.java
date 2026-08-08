package ssg.legoflow.email.imap.client;

import ssg.legoflow.email.imap.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * IMAP4rev2 client providing high-level mailbox operations.
 *
 * <p>Supports connection, login, mailbox selection, message fetch, store,
 * search, sort, copy, move, and IDLE push notifications. Built on
 * {@link ImapConnection} for low-level protocol I/O.
 *
 * @since 0.1.0
 */
public final class ImapClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ImapClient.class);

    private final ImapClientConfig config;
    private final ImapConnection connection;
    private volatile FolderView selectedFolder;

    /**
     * Creates an IMAP client with the given configuration.
     *
     * @param config the client configuration
     */
    public ImapClient(ImapClientConfig config) {
        this.config = Objects.requireNonNull(config);
        this.connection = new ImapConnection(config);
    }

    /**
     * Connects to the server and logs in.
     *
     * @throws IOException if connection or login fails
     */
    public void connect() throws IOException {
        connection.connect();
    }

    /**
     * Logs in with the configured credentials.
     *
     * @return true if login succeeded
     * @throws IOException if an I/O error occurs
     */
    public boolean login() throws IOException {
        return login(config.username(), config.password());
    }

    /**
     * Logs in with the given credentials.
     *
     * @param username the username
     * @param password the password
     * @return true if login succeeded
     * @throws IOException if an I/O error occurs
     */
    public boolean login(String username, String password) throws IOException {
        String quotedUser = ImapCodec.quoteString(username);
        String quotedPass = ImapCodec.quoteString(password);
        List<ImapResponse> responses = connection.executeCommand(
                ImapCommand.LOGIN, quotedUser, quotedPass);

        ImapResponse tagged = findTagged(responses);
        if (tagged != null && tagged.isOk()) {
            // Update capabilities from response
            if (tagged.responseCode() != null && tagged.responseCode().startsWith("CAPABILITY")) {
                connection.updateCapabilities(
                        tagged.responseCode().substring("CAPABILITY".length()).trim());
            }
            return true;
        }
        return false;
    }

    /**
     * Selects a mailbox (read-write).
     *
     * @param mailboxName the mailbox name
     * @return the folder view, or null if selection failed
     * @throws IOException if an I/O error occurs
     */
    public FolderView select(String mailboxName) throws IOException {
        return selectOrExamine(mailboxName, false);
    }

    /**
     * Examines a mailbox (read-only).
     *
     * @param mailboxName the mailbox name
     * @return the folder view, or null if examination failed
     * @throws IOException if an I/O error occurs
     */
    public FolderView examine(String mailboxName) throws IOException {
        return selectOrExamine(mailboxName, true);
    }

    private FolderView selectOrExamine(String mailboxName, boolean readOnly) throws IOException {
        ImapCommand cmd = readOnly ? ImapCommand.EXAMINE : ImapCommand.SELECT;
        List<ImapResponse> responses = connection.executeCommand(cmd,
                ImapCodec.quoteString(mailboxName));

        ImapResponse tagged = findTagged(responses);
        if (tagged == null || !tagged.isOk()) return null;

        FolderView view = new FolderView(mailboxName);
        view.setReadOnly(readOnly || (tagged.responseCode() != null
                && tagged.responseCode().contains("READ-ONLY")));

        for (ImapResponse resp : responses) {
            if (!resp.isUntagged()) continue;
            String text = resp.text();
            if (text == null) continue;

            if (text.endsWith("EXISTS")) {
                view.setMessageCount(parseNumber(text));
            } else if (text.endsWith("RECENT")) {
                view.setRecentCount(parseNumber(text));
            } else if (resp.status() == ImapStatus.OK && resp.responseCode() != null) {
                String code = resp.responseCode();
                if (code.startsWith("UIDVALIDITY")) {
                    view.setUidValidity(parseLongAfter(code, "UIDVALIDITY"));
                } else if (code.startsWith("UIDNEXT")) {
                    view.setUidNext(parseLongAfter(code, "UIDNEXT"));
                } else if (code.startsWith("UNSEEN")) {
                    view.setFirstUnseen((int) parseLongAfter(code, "UNSEEN"));
                } else if (code.startsWith("HIGHESTMODSEQ")) {
                    view.setHighestModSeq(parseLongAfter(code, "HIGHESTMODSEQ"));
                } else if (code.startsWith("PERMANENTFLAGS")) {
                    String flagStr = code.substring("PERMANENTFLAGS".length()).trim();
                    view.setPermanentFlags(ImapCodec.parseFlags(flagStr));
                }
            } else if (text.startsWith("FLAGS")) {
                String flagStr = text.substring("FLAGS".length()).trim();
                view.setFlags(ImapCodec.parseFlags(flagStr));
            }
        }

        selectedFolder = view;
        return view;
    }

    /**
     * Fetches data items for the given message sequence set.
     *
     * @param sequenceSet the sequence set (e.g., "1:*", "1,3,5")
     * @param items       the data items to fetch
     * @return the list of fetch results
     * @throws IOException if an I/O error occurs
     */
    public List<FetchResult> fetch(String sequenceSet, String items) throws IOException {
        List<ImapResponse> responses = connection.executeCommand(
                ImapCommand.FETCH, sequenceSet, items);
        return parseFetchResponses(responses);
    }

    /**
     * Fetches data items using UID FETCH.
     *
     * @param uidSet the UID set
     * @param items  the data items
     * @return the list of fetch results
     * @throws IOException if an I/O error occurs
     */
    public List<FetchResult> uidFetch(String uidSet, String items) throws IOException {
        String tag = connection.nextTag();
        List<ImapResponse> responses = connection.executeRaw(
                tag + " UID FETCH " + uidSet + " " + items);
        return parseFetchResponses(responses);
    }

    /**
     * Stores flags on messages.
     *
     * @param sequenceSet the sequence set
     * @param dataItem    the flag operation (e.g., "+FLAGS", "-FLAGS", "FLAGS")
     * @param flags       the flags
     * @return the updated fetch results (unless .SILENT)
     * @throws IOException if an I/O error occurs
     */
    public List<FetchResult> store(String sequenceSet, String dataItem, String flags)
            throws IOException {
        List<ImapResponse> responses = connection.executeCommand(
                ImapCommand.STORE, sequenceSet, dataItem, flags);
        return parseFetchResponses(responses);
    }

    /**
     * Searches for messages matching the given criteria.
     *
     * @param criteria the search criteria string
     * @return the list of matching sequence numbers
     * @throws IOException if an I/O error occurs
     */
    public List<Integer> search(String criteria) throws IOException {
        List<ImapResponse> responses = connection.executeCommand(
                ImapCommand.SEARCH, criteria);
        return parseSearchResponse(responses);
    }

    /**
     * Searches for messages and returns UIDs.
     *
     * @param criteria the search criteria
     * @return the list of matching UIDs
     * @throws IOException if an I/O error occurs
     */
    public List<Long> uidSearch(String criteria) throws IOException {
        String tag = connection.nextTag();
        List<ImapResponse> responses = connection.executeRaw(
                tag + " UID SEARCH " + criteria);
        return parseSearchResponse(responses).stream()
                .map(Long::valueOf).toList();
    }

    /**
     * Copies messages to another mailbox.
     *
     * @param sequenceSet the sequence set
     * @param targetMailbox the target mailbox name
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean copy(String sequenceSet, String targetMailbox) throws IOException {
        ImapResponse resp = connection.executeForStatus(ImapCommand.COPY,
                sequenceSet, ImapCodec.quoteString(targetMailbox));
        return resp.isOk();
    }

    /**
     * Moves messages to another mailbox (MOVE extension).
     *
     * @param sequenceSet the sequence set
     * @param targetMailbox the target mailbox name
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean move(String sequenceSet, String targetMailbox) throws IOException {
        ImapResponse resp = connection.executeForStatus(ImapCommand.MOVE,
                sequenceSet, ImapCodec.quoteString(targetMailbox));
        return resp.isOk();
    }

    /**
     * Creates a new mailbox.
     *
     * @param name the mailbox name
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean create(String name) throws IOException {
        ImapResponse resp = connection.executeForStatus(ImapCommand.CREATE,
                ImapCodec.quoteString(name));
        return resp.isOk();
    }

    /**
     * Deletes a mailbox.
     *
     * @param name the mailbox name
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean delete(String name) throws IOException {
        ImapResponse resp = connection.executeForStatus(ImapCommand.DELETE,
                ImapCodec.quoteString(name));
        return resp.isOk();
    }

    /**
     * Renames a mailbox.
     *
     * @param oldName the current name
     * @param newName the new name
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean rename(String oldName, String newName) throws IOException {
        ImapResponse resp = connection.executeForStatus(ImapCommand.RENAME,
                ImapCodec.quoteString(oldName), ImapCodec.quoteString(newName));
        return resp.isOk();
    }

    /**
     * Lists mailboxes matching the pattern.
     *
     * @param reference the reference (namespace prefix)
     * @param pattern   the pattern (supports * and %)
     * @return the list of mailbox names
     * @throws IOException if an I/O error occurs
     */
    public List<String> list(String reference, String pattern) throws IOException {
        List<ImapResponse> responses = connection.executeCommand(ImapCommand.LIST,
                ImapCodec.quoteString(reference), ImapCodec.quoteString(pattern));
        List<String> names = new ArrayList<>();
        for (ImapResponse resp : responses) {
            if (resp.isUntagged() && resp.text() != null && resp.text().startsWith("LIST")) {
                // Extract mailbox name from LIST response
                String text = resp.text();
                int lastSpace = text.lastIndexOf(' ');
                if (lastSpace > 0) {
                    names.add(ImapCodec.unquoteString(text.substring(lastSpace + 1).trim()));
                }
            }
        }
        return names;
    }

    /**
     * Gets the STATUS of a mailbox.
     *
     * @param mailboxName the mailbox name
     * @param items       the status items (e.g., "(MESSAGES RECENT UNSEEN)")
     * @return the status response data, or null
     * @throws IOException if an I/O error occurs
     */
    public Map<String, Long> status(String mailboxName, String items) throws IOException {
        List<ImapResponse> responses = connection.executeCommand(ImapCommand.STATUS,
                ImapCodec.quoteString(mailboxName), items);
        Map<String, Long> result = new LinkedHashMap<>();
        for (ImapResponse resp : responses) {
            if (resp.isUntagged() && resp.text() != null && resp.text().startsWith("STATUS")) {
                String text = resp.text();
                int parenStart = text.indexOf('(');
                int parenEnd = text.indexOf(')');
                if (parenStart >= 0 && parenEnd > parenStart) {
                    String[] tokens = text.substring(parenStart + 1, parenEnd).split("\\s+");
                    for (int i = 0; i + 1 < tokens.length; i += 2) {
                        result.put(tokens[i], Long.parseLong(tokens[i + 1]));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Expunges deleted messages from the selected mailbox.
     *
     * @return the list of expunged sequence numbers
     * @throws IOException if an I/O error occurs
     */
    public List<Integer> expunge() throws IOException {
        List<ImapResponse> responses = connection.executeCommand(ImapCommand.EXPUNGE);
        List<Integer> expunged = new ArrayList<>();
        for (ImapResponse resp : responses) {
            if (resp.isUntagged() && resp.text() != null && resp.text().endsWith("EXPUNGE")) {
                expunged.add(parseNumber(resp.text()));
            }
        }
        return expunged;
    }

    /**
     * Closes the selected mailbox (expunging deleted messages).
     *
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean closeMailbox() throws IOException {
        ImapResponse resp = connection.executeForStatus(ImapCommand.CLOSE);
        if (resp.isOk()) {
            selectedFolder = null;
            return true;
        }
        return false;
    }

    /**
     * Unselects the current mailbox without expunging.
     *
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean unselect() throws IOException {
        ImapResponse resp = connection.executeForStatus(ImapCommand.UNSELECT);
        if (resp.isOk()) {
            selectedFolder = null;
            return true;
        }
        return false;
    }

    /**
     * Sends NOOP to poll for updates.
     *
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean noop() throws IOException {
        return connection.executeForStatus(ImapCommand.NOOP).isOk();
    }

    /**
     * Gets server capabilities.
     *
     * @return the capability list
     * @throws IOException if an I/O error occurs
     */
    public List<String> capability() throws IOException {
        connection.executeCommand(ImapCommand.CAPABILITY);
        return connection.capabilities();
    }

    /**
     * Gets the NAMESPACE configuration.
     *
     * @return the raw NAMESPACE response
     * @throws IOException if an I/O error occurs
     */
    public String namespace() throws IOException {
        List<ImapResponse> responses = connection.executeCommand(ImapCommand.NAMESPACE);
        for (ImapResponse resp : responses) {
            if (resp.isUntagged() && resp.text() != null && resp.text().startsWith("NAMESPACE")) {
                return resp.text().substring("NAMESPACE".length()).trim();
            }
        }
        return null;
    }

    /**
     * Subscribes to a mailbox.
     *
     * @param mailboxName the mailbox name
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean subscribe(String mailboxName) throws IOException {
        return connection.executeForStatus(ImapCommand.SUBSCRIBE,
                ImapCodec.quoteString(mailboxName)).isOk();
    }

    /**
     * Unsubscribes from a mailbox.
     *
     * @param mailboxName the mailbox name
     * @return true if successful
     * @throws IOException if an I/O error occurs
     */
    public boolean unsubscribe(String mailboxName) throws IOException {
        return connection.executeForStatus(ImapCommand.UNSUBSCRIBE,
                ImapCodec.quoteString(mailboxName)).isOk();
    }

    /**
     * Logs out from the server.
     *
     * @throws IOException if an I/O error occurs
     */
    public void logout() throws IOException {
        connection.executeCommand(ImapCommand.LOGOUT);
    }

    /**
     * Returns the currently selected folder view.
     *
     * @return the folder view, or null if no mailbox is selected
     */
    public FolderView selectedFolder() { return selectedFolder; }

    /**
     * Returns the underlying connection.
     *
     * @return the connection
     */
    public ImapConnection connection() { return connection; }

    @Override
    public void close() throws IOException {
        try {
            if (connection.isConnected()) {
                logout();
            }
        } finally {
            connection.close();
        }
    }

    // --- Parse helpers ---

    private ImapResponse findTagged(List<ImapResponse> responses) {
        return responses.stream()
                .filter(ImapResponse::isTagged)
                .findFirst()
                .orElse(null);
    }

    private List<FetchResult> parseFetchResponses(List<ImapResponse> responses) {
        List<FetchResult> results = new ArrayList<>();
        for (ImapResponse resp : responses) {
            if (resp.isUntagged() && resp.text() != null) {
                FetchResult fr = FetchResult.parse(resp.rawLine());
                if (fr != null) {
                    results.add(fr);
                }
            }
        }
        return results;
    }

    private List<Integer> parseSearchResponse(List<ImapResponse> responses) {
        for (ImapResponse resp : responses) {
            if (resp.isUntagged() && resp.text() != null
                    && resp.text().startsWith("SEARCH")) {
                String nums = resp.text().substring("SEARCH".length()).trim();
                if (nums.isEmpty()) return List.of();
                return Arrays.stream(nums.split("\\s+"))
                        .map(Integer::parseInt)
                        .toList();
            }
        }
        return List.of();
    }

    private static int parseNumber(String text) {
        String[] parts = text.trim().split("\\s+");
        for (String part : parts) {
            try {
                return Integer.parseInt(part);
            } catch (NumberFormatException e) {
                // continue
            }
        }
        return 0;
    }

    private static long parseLongAfter(String text, String prefix) {
        String after = text.substring(prefix.length()).trim();
        return Long.parseLong(after.split("\\s+")[0]);
    }
}
