package ssg.legoflow.email.imap.server;

import ssg.legoflow.email.imap.protocol.*;
import ssg.legoflow.email.imap.protocol.ImapCommand.ImapState;
import ssg.legoflow.email.imap.condstore.ModSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Per-client IMAP session managing connection state, command processing,
 * and mailbox operations.
 *
 * <p>Handles the full IMAP4rev2 command set including LOGIN, SELECT, FETCH,
 * STORE, SEARCH, SORT, COPY, MOVE, IDLE, NAMESPACE, and all mailbox
 * management commands. Transitions between connection states
 * (Not Authenticated, Authenticated, Selected, Logout) as commands execute.
 *
 * @since 0.1.0
 */
public final class ImapSession implements Runnable, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ImapSession.class);

    private static final List<String> CAPABILITIES = List.of(
            "IMAP4rev2", "IDLE", "NAMESPACE", "CONDSTORE",
            "SORT", "THREAD=ORDEREDSUBJECT", "THREAD=REFERENCES",
            "MOVE", "LIST-EXTENDED", "LITERAL+", "UNSELECT");

    private final Socket socket;
    private final MailStore store;
    private final IdleNotifier idleNotifier;
    private final NamespaceConfig namespaceConfig;

    private volatile ImapState state = ImapState.NOT_AUTHENTICATED;
    private volatile String authenticatedUser;
    private volatile Mailbox selectedMailbox;
    private volatile boolean selectedReadOnly;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private BufferedReader reader;
    private PrintWriter writer;

    /**
     * Creates a new IMAP session.
     *
     * @param socket          the client socket
     * @param store           the mail store
     * @param idleNotifier    the idle notifier
     * @param namespaceConfig the namespace configuration
     */
    public ImapSession(Socket socket, MailStore store, IdleNotifier idleNotifier,
                       NamespaceConfig namespaceConfig) {
        this.socket = Objects.requireNonNull(socket);
        this.store = Objects.requireNonNull(store);
        this.idleNotifier = Objects.requireNonNull(idleNotifier);
        this.namespaceConfig = Objects.requireNonNull(namespaceConfig);
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Send greeting
            sendLine("* OK [CAPABILITY " + String.join(" ", CAPABILITIES) + "] IMAP4rev2 server ready");

            while (running.get()) {
                String line = reader.readLine();
                if (line == null) break;
                processLine(line);
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.debug("Session I/O error: {}", e.getMessage());
            }
        } finally {
            close();
        }
    }

    /**
     * Processes a single IMAP command line.
     *
     * @param line the raw command line
     */
    void processLine(String line) {
        String[] parts = ImapCodec.parseCommandLine(line);
        if (parts == null || parts.length < 2) {
            sendLine("* BAD Invalid command");
            return;
        }

        String tag = parts[0];
        String commandStr = parts[1].toUpperCase();
        String args = parts.length > 2 ? parts[2] : "";

        // Handle UID prefix
        boolean uidMode = false;
        if ("UID".equals(commandStr) && !args.isEmpty()) {
            uidMode = true;
            int space = args.indexOf(' ');
            if (space > 0) {
                commandStr = args.substring(0, space).toUpperCase();
                args = args.substring(space + 1);
            } else {
                commandStr = args.toUpperCase();
                args = "";
            }
        }

        ImapCommand command;
        try {
            command = ImapCommand.parse(commandStr);
        } catch (IllegalArgumentException e) {
            sendLine(tag + " BAD Unknown command: " + commandStr);
            return;
        }

        // Check state
        if (!isCommandAllowed(command)) {
            sendLine(tag + " BAD Command not allowed in current state");
            return;
        }

        try {
            processCommand(tag, command, args, uidMode);
        } catch (Exception e) {
            LOG.error("Error processing command {} {}", tag, command, e);
            sendLine(tag + " BAD Internal server error");
        }
    }

    private boolean isCommandAllowed(ImapCommand command) {
        return switch (command.requiredState()) {
            case ANY -> true;
            case NOT_AUTHENTICATED -> state == ImapState.NOT_AUTHENTICATED;
            case AUTHENTICATED -> state == ImapState.AUTHENTICATED || state == ImapState.SELECTED;
            case SELECTED -> state == ImapState.SELECTED;
        };
    }

    private void processCommand(String tag, ImapCommand command, String args, boolean uidMode) throws IOException {
        switch (command) {
            case CAPABILITY -> handleCapability(tag);
            case NOOP -> handleNoop(tag);
            case LOGOUT -> handleLogout(tag);
            case LOGIN -> handleLogin(tag, args);
            case AUTHENTICATE -> handleAuthenticate(tag, args);
            case STARTTLS -> sendLine(tag + " BAD STARTTLS not supported in plaintext mode");
            case SELECT -> handleSelect(tag, args, false);
            case EXAMINE -> handleSelect(tag, args, true);
            case CREATE -> handleCreate(tag, args);
            case DELETE -> handleDelete(tag, args);
            case RENAME -> handleRename(tag, args);
            case SUBSCRIBE -> handleSubscribe(tag, args);
            case UNSUBSCRIBE -> handleUnsubscribe(tag, args);
            case LIST -> handleList(tag, args);
            case NAMESPACE -> handleNamespace(tag);
            case STATUS -> handleStatus(tag, args);
            case APPEND -> handleAppend(tag, args);
            case IDLE -> handleIdle(tag);
            case FETCH -> handleFetch(tag, args, uidMode);
            case STORE -> handleStore(tag, args, uidMode);
            case COPY -> handleCopy(tag, args, uidMode);
            case MOVE -> handleMove(tag, args, uidMode);
            case SEARCH -> handleSearch(tag, args, uidMode);
            case SORT -> handleSort(tag, args);
            case THREAD -> handleThread(tag, args);
            case EXPUNGE -> handleExpunge(tag);
            case CLOSE -> handleClose(tag);
            case UNSELECT -> handleUnselect(tag);
            case UID -> sendLine(tag + " BAD UID requires a sub-command");
        }
    }

    // --- Command handlers ---

    private void handleCapability(String tag) {
        sendLine("* CAPABILITY " + String.join(" ", CAPABILITIES));
        sendLine(tag + " OK CAPABILITY completed");
    }

    private void handleNoop(String tag) {
        sendLine(tag + " OK NOOP completed");
    }

    private void handleLogout(String tag) {
        sendLine("* BYE IMAP4rev2 server logging out");
        sendLine(tag + " OK LOGOUT completed");
        running.set(false);
    }

    private void handleLogin(String tag, String args) {
        String[] loginArgs = parseLoginArgs(args);
        if (loginArgs == null || loginArgs.length < 2) {
            sendLine(tag + " BAD LOGIN requires username and password");
            return;
        }
        String username = loginArgs[0];
        String password = loginArgs[1];

        if (store.authenticate(username, password)) {
            authenticatedUser = username;
            state = ImapState.AUTHENTICATED;
            sendLine(tag + " OK [CAPABILITY " + String.join(" ", CAPABILITIES) + "] LOGIN completed");
        } else {
            sendLine(tag + " NO [AUTHENTICATIONFAILED] Invalid credentials");
        }
    }

    private void handleAuthenticate(String tag, String args) {
        // Simplified: only PLAIN mechanism
        if (args.toUpperCase().startsWith("PLAIN")) {
            sendLine("+ ");
            // In a real implementation, we'd read the Base64 response
            sendLine(tag + " NO AUTHENTICATE not fully implemented");
        } else {
            sendLine(tag + " NO Unsupported mechanism");
        }
    }

    private void handleSelect(String tag, String args, boolean readOnly) {
        String name = unquoteArg(args.trim());
        Mailbox mailbox = store.getMailbox(name);
        if (mailbox == null) {
            sendLine(tag + " NO [NONEXISTENT] Mailbox does not exist");
            return;
        }

        selectedMailbox = mailbox;
        selectedReadOnly = readOnly;
        state = ImapState.SELECTED;

        sendLine("* " + mailbox.messageCount() + " EXISTS");
        sendLine("* " + mailbox.recentCount() + " RECENT");
        sendLine("* OK [UIDVALIDITY " + mailbox.uidValidity() + "] UIDs valid");
        sendLine("* OK [UIDNEXT " + mailbox.uidNext() + "] Predicted next UID");
        sendLine("* FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)");
        sendLine("* OK [PERMANENTFLAGS (" + String.join(" ", mailbox.permanentFlags()) + ")] Permanent flags");
        int firstUnseen = mailbox.firstUnseen();
        if (firstUnseen > 0) {
            sendLine("* OK [UNSEEN " + firstUnseen + "] First unseen message");
        }
        sendLine("* OK [HIGHESTMODSEQ " + mailbox.highestModSeq() + "] Highest mod-sequence");

        String rwMode = readOnly ? "READ-ONLY" : "READ-WRITE";
        sendLine(tag + " OK [" + rwMode + "] " + (readOnly ? "EXAMINE" : "SELECT") + " completed");
    }

    private void handleCreate(String tag, String args) {
        String name = unquoteArg(args.trim());
        try {
            store.createMailbox(name);
            sendLine(tag + " OK CREATE completed");
        } catch (IllegalArgumentException e) {
            sendLine(tag + " NO CREATE failed: " + e.getMessage());
        }
    }

    private void handleDelete(String tag, String args) {
        String name = unquoteArg(args.trim());
        if (store.deleteMailbox(name)) {
            sendLine(tag + " OK DELETE completed");
        } else {
            sendLine(tag + " NO DELETE failed");
        }
    }

    private void handleRename(String tag, String args) {
        String[] names = splitArgs(args, 2);
        if (names.length < 2) {
            sendLine(tag + " BAD RENAME requires old and new names");
            return;
        }
        if (store.renameMailbox(unquoteArg(names[0]), unquoteArg(names[1]))) {
            sendLine(tag + " OK RENAME completed");
        } else {
            sendLine(tag + " NO RENAME failed");
        }
    }

    private void handleSubscribe(String tag, String args) {
        String name = unquoteArg(args.trim());
        Mailbox mailbox = store.getMailbox(name);
        if (mailbox != null) {
            mailbox.subscribe(authenticatedUser);
            sendLine(tag + " OK SUBSCRIBE completed");
        } else {
            sendLine(tag + " NO Mailbox does not exist");
        }
    }

    private void handleUnsubscribe(String tag, String args) {
        String name = unquoteArg(args.trim());
        Mailbox mailbox = store.getMailbox(name);
        if (mailbox != null) {
            mailbox.unsubscribe(authenticatedUser);
            sendLine(tag + " OK UNSUBSCRIBE completed");
        } else {
            sendLine(tag + " NO Mailbox does not exist");
        }
    }

    private void handleList(String tag, String args) {
        String[] listArgs = splitArgs(args, 2);
        String reference = listArgs.length > 0 ? unquoteArg(listArgs[0]) : "";
        String pattern = listArgs.length > 1 ? unquoteArg(listArgs[1]) : "*";

        if (pattern.isEmpty()) {
            // Special case: return hierarchy delimiter
            sendLine("* LIST (\\Noselect) \"" + store.delimiter() + "\" \"\"");
            sendLine(tag + " OK LIST completed");
            return;
        }

        List<String> names = store.listMailboxes(reference, pattern);
        for (String name : names) {
            Mailbox mb = store.getMailbox(name);
            String attrs = "";
            if (mb != null && authenticatedUser != null && mb.isSubscribed(authenticatedUser)) {
                attrs = "\\Subscribed";
            }
            sendLine("* LIST (" + attrs + ") \"" + store.delimiter() + "\" " +
                    ImapCodec.quoteString(name));
        }
        sendLine(tag + " OK LIST completed");
    }

    private void handleNamespace(String tag) {
        sendLine("* NAMESPACE " + namespaceConfig.toWire());
        sendLine(tag + " OK NAMESPACE completed");
    }

    private void handleStatus(String tag, String args) {
        int parenStart = args.indexOf('(');
        if (parenStart < 0) {
            sendLine(tag + " BAD STATUS requires mailbox and status items");
            return;
        }
        String name = unquoteArg(args.substring(0, parenStart).trim());
        String itemsStr = args.substring(parenStart);

        Mailbox mailbox = store.getMailbox(name);
        if (mailbox == null) {
            sendLine(tag + " NO [NONEXISTENT] Mailbox does not exist");
            return;
        }

        List<String> items = ImapCodec.parseParenList(itemsStr);
        StringBuilder result = new StringBuilder();
        result.append("* STATUS ").append(ImapCodec.quoteString(name)).append(" (");
        boolean first = true;
        for (String item : items) {
            if (!first) result.append(' ');
            first = false;
            String upper = item.toUpperCase();
            result.append(upper).append(' ');
            result.append(switch (upper) {
                case "MESSAGES" -> mailbox.messageCount();
                case "RECENT" -> mailbox.recentCount();
                case "UIDNEXT" -> mailbox.uidNext();
                case "UIDVALIDITY" -> mailbox.uidValidity();
                case "UNSEEN" -> mailbox.unseenCount();
                case "HIGHESTMODSEQ" -> mailbox.highestModSeq();
                default -> "0";
            });
        }
        result.append(')');
        sendLine(result.toString());
        sendLine(tag + " OK STATUS completed");
    }

    private void handleAppend(String tag, String args) throws IOException {
        // APPEND mailbox [flags] [datetime] literal
        // Simplified: parse mailbox and flags, read literal
        int flagStart = args.indexOf('(');
        int flagEnd = args.indexOf(')');
        String mailboxName;
        Set<String> flags = new HashSet<>();

        if (flagStart >= 0 && flagEnd > flagStart) {
            mailboxName = unquoteArg(args.substring(0, flagStart).trim());
            String flagStr = args.substring(flagStart + 1, flagEnd);
            for (String f : flagStr.split("\\s+")) {
                if (!f.isEmpty()) flags.add(f);
            }
        } else {
            // Find literal marker
            int litStart = args.indexOf('{');
            if (litStart > 0) {
                mailboxName = unquoteArg(args.substring(0, litStart).trim());
            } else {
                mailboxName = unquoteArg(args.trim());
            }
        }

        Mailbox mailbox = store.getMailbox(mailboxName);
        if (mailbox == null) {
            sendLine(tag + " NO [TRYCREATE] Mailbox does not exist");
            return;
        }

        // Find literal size
        int litStart = args.indexOf('{');
        int litEnd = args.indexOf('}');
        if (litStart >= 0 && litEnd > litStart) {
            String litHeader = args.substring(litStart, litEnd + 1);
            long[] parsed = ImapLiteral.parseLiteralHeader(litHeader);
            int size = (int) parsed[0];
            boolean nonSync = parsed[1] == 1;

            if (!nonSync) {
                sendLine("+ Ready for literal data");
            }

            String data = ImapCodec.readLiteral(reader, size);
            // Read trailing CRLF
            reader.readLine();

            StoredMessage msg = mailbox.append(data.getBytes(StandardCharsets.UTF_8), flags, Instant.now());
            idleNotifier.notifyExists(mailboxName, mailbox.messageCount());
            sendLine(tag + " OK [APPENDUID " + mailbox.uidValidity() + " " + msg.uid() + "] APPEND completed");
        } else {
            sendLine(tag + " BAD APPEND requires message literal");
        }
    }

    private void handleIdle(String tag) throws IOException {
        sendLine("+ idling");

        Consumer<String> listener = this::sendLine;
        String mailboxName = selectedMailbox != null ? selectedMailbox.name() : "INBOX";
        idleNotifier.register(mailboxName, listener);

        try {
            // Wait for DONE
            while (running.get()) {
                String line = reader.readLine();
                if (line == null) break;
                if (line.trim().equalsIgnoreCase("DONE")) break;
            }
        } finally {
            idleNotifier.unregister(mailboxName, listener);
        }

        sendLine(tag + " OK IDLE terminated");
    }

    private void handleFetch(String tag, String args, boolean uidMode) {
        if (selectedMailbox == null) {
            sendLine(tag + " NO No mailbox selected");
            return;
        }

        // Parse sequence set and data items
        int space = args.indexOf(' ');
        if (space < 0) {
            sendLine(tag + " BAD FETCH requires sequence set and data items");
            return;
        }
        String seqSet = args.substring(0, space);
        String itemsStr = args.substring(space + 1).trim();

        // Expand macros
        itemsStr = expandFetchMacro(itemsStr);

        List<FetchDataItem> items = parseFetchItems(itemsStr);

        // Resolve sequence set
        List<StoredMessage> messagesToFetch = resolveMessages(seqSet, uidMode);

        for (StoredMessage msg : messagesToFetch) {
            int seqNum = selectedMailbox.index().seqNumForUid(msg.uid());
            if (seqNum < 0) continue;

            // Add UID to items in UID mode
            List<FetchDataItem> fetchItems = uidMode
                    ? addUidItem(items) : items;

            String response = FetchHandler.fetch(msg, seqNum, fetchItems, !selectedReadOnly);
            sendLine(response);
        }
        sendLine(tag + " OK " + (uidMode ? "UID " : "") + "FETCH completed");
    }

    private void handleStore(String tag, String args, boolean uidMode) {
        if (selectedMailbox == null || selectedReadOnly) {
            sendLine(tag + " NO " + (selectedMailbox == null ? "No mailbox" : "Read-only"));
            return;
        }

        // Parse: sequence_set data_item flags
        String[] storeParts = args.split("\\s+", 3);
        if (storeParts.length < 3) {
            sendLine(tag + " BAD STORE requires sequence set, data item, and flags");
            return;
        }

        String seqSet = storeParts[0];
        String dataItem = storeParts[1].toUpperCase();
        String flagsStr = storeParts[2];

        boolean silent = dataItem.contains(".SILENT");
        Mailbox.FlagOperation op;
        if (dataItem.startsWith("+FLAGS")) {
            op = Mailbox.FlagOperation.ADD;
        } else if (dataItem.startsWith("-FLAGS")) {
            op = Mailbox.FlagOperation.REMOVE;
        } else {
            op = Mailbox.FlagOperation.SET;
        }

        Set<String> flags = new HashSet<>(ImapCodec.parseFlags(flagsStr));
        List<StoredMessage> messages = resolveMessages(seqSet, uidMode);

        for (StoredMessage msg : messages) {
            StoredMessage updated = selectedMailbox.storeFlags(msg.uid(), flags, op);
            if (updated != null && !silent) {
                int seqNum = selectedMailbox.index().seqNumForUid(updated.uid());
                sendLine("* " + seqNum + " FETCH (FLAGS " +
                        FetchHandler.formatFlags(updated.flags()) + ")");
            }
        }
        sendLine(tag + " OK " + (uidMode ? "UID " : "") + "STORE completed");
    }

    private void handleCopy(String tag, String args, boolean uidMode) {
        if (selectedMailbox == null) {
            sendLine(tag + " NO No mailbox selected");
            return;
        }

        String[] copyArgs = splitArgs(args, 2);
        if (copyArgs.length < 2) {
            sendLine(tag + " BAD COPY requires sequence set and mailbox name");
            return;
        }

        String seqSet = copyArgs[0];
        String targetName = unquoteArg(copyArgs[1]);
        Mailbox target = store.getMailbox(targetName);
        if (target == null) {
            sendLine(tag + " NO [TRYCREATE] Target mailbox does not exist");
            return;
        }

        List<StoredMessage> messages = resolveMessages(seqSet, uidMode);
        List<Long> newUids = new ArrayList<>();
        for (StoredMessage msg : messages) {
            long newUid = selectedMailbox.copyMessage(msg.uid(), target);
            if (newUid >= 0) newUids.add(newUid);
        }

        idleNotifier.notifyExists(targetName, target.messageCount());
        sendLine(tag + " OK " + (uidMode ? "UID " : "") + "COPY completed");
    }

    private void handleMove(String tag, String args, boolean uidMode) {
        if (selectedMailbox == null || selectedReadOnly) {
            sendLine(tag + " NO " + (selectedMailbox == null ? "No mailbox" : "Read-only"));
            return;
        }

        String[] moveArgs = splitArgs(args, 2);
        if (moveArgs.length < 2) {
            sendLine(tag + " BAD MOVE requires sequence set and mailbox name");
            return;
        }

        String seqSet = moveArgs[0];
        String targetName = unquoteArg(moveArgs[1]);
        Mailbox target = store.getMailbox(targetName);
        if (target == null) {
            sendLine(tag + " NO [TRYCREATE] Target mailbox does not exist");
            return;
        }

        List<StoredMessage> messages = resolveMessages(seqSet, uidMode);
        for (StoredMessage msg : messages) {
            int seqNum = selectedMailbox.index().seqNumForUid(msg.uid());
            selectedMailbox.moveMessage(msg.uid(), target);
            if (seqNum > 0) {
                sendLine("* " + seqNum + " EXPUNGE");
            }
        }

        idleNotifier.notifyExists(targetName, target.messageCount());
        sendLine(tag + " OK " + (uidMode ? "UID " : "") + "MOVE completed");
    }

    private void handleSearch(String tag, String args, boolean uidMode) {
        if (selectedMailbox == null) {
            sendLine(tag + " NO No mailbox selected");
            return;
        }

        SearchCriteria criteria = parseSearchArgs(args);
        List<Long> uids = SearchEngine.searchUids(selectedMailbox, criteria);

        StringBuilder result = new StringBuilder("* SEARCH");
        for (long uid : uids) {
            if (uidMode) {
                result.append(' ').append(uid);
            } else {
                int seqNum = selectedMailbox.index().seqNumForUid(uid);
                if (seqNum > 0) result.append(' ').append(seqNum);
            }
        }
        sendLine(result.toString());
        sendLine(tag + " OK " + (uidMode ? "UID " : "") + "SEARCH completed");
    }

    private void handleSort(String tag, String args) {
        if (selectedMailbox == null) {
            sendLine(tag + " NO No mailbox selected");
            return;
        }

        // Parse: (sort_criteria) charset search_criteria
        int parenEnd = args.indexOf(')');
        if (parenEnd < 0) {
            sendLine(tag + " BAD SORT requires sort criteria");
            return;
        }

        String sortStr = args.substring(0, parenEnd + 1);
        String rest = args.substring(parenEnd + 1).trim();

        // Skip charset
        int nextSpace = rest.indexOf(' ');
        String searchArgs = nextSpace >= 0 ? rest.substring(nextSpace + 1) : "ALL";

        List<SortCriteria> sortCriteria = parseSortCriteria(sortStr);
        SearchCriteria searchCriteria = parseSearchArgs(searchArgs);

        List<Long> sorted = SortEngine.sort(selectedMailbox, sortCriteria, searchCriteria);

        StringBuilder result = new StringBuilder("* SORT");
        for (long uid : sorted) {
            result.append(' ').append(uid);
        }
        sendLine(result.toString());
        sendLine(tag + " OK SORT completed");
    }

    private void handleThread(String tag, String args) {
        if (selectedMailbox == null) {
            sendLine(tag + " NO No mailbox selected");
            return;
        }

        // Parse: algorithm charset search_criteria
        String[] threadArgs = args.split("\\s+", 3);
        if (threadArgs.length < 2) {
            sendLine(tag + " BAD THREAD requires algorithm and charset");
            return;
        }

        SortCriteria.ThreadAlgorithm algorithm;
        try {
            algorithm = SortCriteria.ThreadAlgorithm.parse(threadArgs[0]);
        } catch (IllegalArgumentException e) {
            sendLine(tag + " BAD Unknown thread algorithm: " + threadArgs[0]);
            return;
        }

        String searchArgs = threadArgs.length > 2 ? threadArgs[2] : "ALL";
        SearchCriteria searchCriteria = parseSearchArgs(searchArgs);

        List<List<Long>> threads = SortEngine.thread(selectedMailbox, algorithm, searchCriteria);

        StringBuilder result = new StringBuilder("* THREAD");
        for (List<Long> thread : threads) {
            result.append(" (");
            for (int i = 0; i < thread.size(); i++) {
                if (i > 0) result.append(' ');
                result.append(thread.get(i));
            }
            result.append(')');
        }
        sendLine(result.toString());
        sendLine(tag + " OK THREAD completed");
    }

    private void handleExpunge(String tag) {
        if (selectedMailbox == null || selectedReadOnly) {
            sendLine(tag + " NO " + (selectedMailbox == null ? "No mailbox" : "Read-only"));
            return;
        }

        // Need to track sequence numbers before removal
        List<Long> expunged = new ArrayList<>();
        for (StoredMessage msg : selectedMailbox.allMessages()) {
            if (msg.hasFlag("\\Deleted")) {
                int seqNum = selectedMailbox.index().seqNumForUid(msg.uid());
                expunged.add(msg.uid());
                sendLine("* " + seqNum + " EXPUNGE");
            }
        }

        // Now actually expunge
        selectedMailbox.expunge();

        sendLine(tag + " OK EXPUNGE completed");
    }

    private void handleClose(String tag) {
        if (selectedMailbox != null && !selectedReadOnly) {
            selectedMailbox.expunge();
        }
        selectedMailbox = null;
        selectedReadOnly = false;
        state = ImapState.AUTHENTICATED;
        sendLine(tag + " OK CLOSE completed");
    }

    private void handleUnselect(String tag) {
        selectedMailbox = null;
        selectedReadOnly = false;
        state = ImapState.AUTHENTICATED;
        sendLine(tag + " OK UNSELECT completed");
    }

    // --- Helper methods ---

    private void sendLine(String line) {
        if (writer != null) {
            writer.print(line + "\r\n");
            writer.flush();
        }
    }

    private List<StoredMessage> resolveMessages(String seqSet, boolean uidMode) {
        if (selectedMailbox == null) return List.of();

        List<StoredMessage> result = new ArrayList<>();
        long maxVal = uidMode ? selectedMailbox.uidNext() - 1 : selectedMailbox.messageCount();
        List<Long> nums = ImapCodec.parseSequenceSet(seqSet, maxVal);

        for (long num : nums) {
            StoredMessage msg;
            if (uidMode) {
                msg = selectedMailbox.getMessage(num);
            } else {
                msg = selectedMailbox.getMessageBySeqNum((int) num);
            }
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    private String expandFetchMacro(String items) {
        return switch (items.toUpperCase()) {
            case "ALL" -> "(FLAGS INTERNALDATE RFC822.SIZE ENVELOPE)";
            case "FAST" -> "(FLAGS INTERNALDATE RFC822.SIZE)";
            case "FULL" -> "(FLAGS INTERNALDATE RFC822.SIZE ENVELOPE BODY)";
            default -> items;
        };
    }

    private List<FetchDataItem> parseFetchItems(String itemsStr) {
        List<String> items = ImapCodec.parseParenList(itemsStr);
        if (items.isEmpty() && !itemsStr.startsWith("(")) {
            items = List.of(itemsStr);
        }
        List<FetchDataItem> result = new ArrayList<>();
        for (String item : items) {
            result.add(FetchDataItem.parse(item));
        }
        return result;
    }

    private List<FetchDataItem> addUidItem(List<FetchDataItem> items) {
        for (FetchDataItem item : items) {
            if ("UID".equals(item.name())) return items;
        }
        List<FetchDataItem> withUid = new ArrayList<>(items);
        withUid.add(FetchDataItem.UID);
        return withUid;
    }

    private SearchCriteria parseSearchArgs(String args) {
        if (args == null || args.isBlank() || args.equalsIgnoreCase("ALL")) {
            return SearchCriteria.all();
        }
        String upper = args.trim().toUpperCase();
        // Handle simple flag criteria
        return switch (upper) {
            case "SEEN" -> SearchCriteria.seen();
            case "UNSEEN" -> SearchCriteria.unseen();
            case "ANSWERED" -> SearchCriteria.answered();
            case "UNANSWERED" -> SearchCriteria.unanswered();
            case "FLAGGED" -> SearchCriteria.flagged();
            case "UNFLAGGED" -> SearchCriteria.unflagged();
            case "DELETED" -> SearchCriteria.deleted();
            case "UNDELETED" -> SearchCriteria.undeleted();
            case "DRAFT" -> SearchCriteria.draft();
            case "UNDRAFT" -> SearchCriteria.undraft();
            case "NEW" -> SearchCriteria.newMessages();
            case "OLD" -> SearchCriteria.old();
            case "RECENT" -> SearchCriteria.recent();
            default -> parseComplexSearch(args.trim());
        };
    }

    private SearchCriteria parseComplexSearch(String args) {
        // Handle multi-criteria search
        List<SearchCriteria> criteria = new ArrayList<>();
        String[] tokens = args.split("\\s+");
        int i = 0;
        while (i < tokens.length) {
            String token = tokens[i].toUpperCase();
            switch (token) {
                case "OR" -> {
                    if (i + 2 < tokens.length) {
                        SearchCriteria left = parseSearchArgs(tokens[i + 1]);
                        SearchCriteria right = parseSearchArgs(tokens[i + 2]);
                        criteria.add(SearchCriteria.or(left, right));
                        i += 3;
                    } else {
                        i++;
                    }
                }
                case "NOT" -> {
                    if (i + 1 < tokens.length) {
                        criteria.add(SearchCriteria.not(parseSearchArgs(tokens[i + 1])));
                        i += 2;
                    } else {
                        i++;
                    }
                }
                case "SUBJECT" -> {
                    if (i + 1 < tokens.length) {
                        criteria.add(SearchCriteria.subject(unquoteArg(tokens[i + 1])));
                        i += 2;
                    } else i++;
                }
                case "FROM" -> {
                    if (i + 1 < tokens.length) {
                        criteria.add(SearchCriteria.from(unquoteArg(tokens[i + 1])));
                        i += 2;
                    } else i++;
                }
                case "TO" -> {
                    if (i + 1 < tokens.length) {
                        criteria.add(SearchCriteria.to(unquoteArg(tokens[i + 1])));
                        i += 2;
                    } else i++;
                }
                case "LARGER" -> {
                    if (i + 1 < tokens.length) {
                        criteria.add(SearchCriteria.larger(Long.parseLong(tokens[i + 1])));
                        i += 2;
                    } else i++;
                }
                case "SMALLER" -> {
                    if (i + 1 < tokens.length) {
                        criteria.add(SearchCriteria.smaller(Long.parseLong(tokens[i + 1])));
                        i += 2;
                    } else i++;
                }
                default -> {
                    // Try as simple flag criterion
                    try {
                        criteria.add(parseSearchArgs(token));
                    } catch (Exception e) {
                        // Skip unknown tokens
                    }
                    i++;
                }
            }
        }

        if (criteria.isEmpty()) return SearchCriteria.all();
        if (criteria.size() == 1) return criteria.getFirst();
        return SearchCriteria.and(criteria.toArray(new SearchCriteria[0]));
    }

    private List<SortCriteria> parseSortCriteria(String sortStr) {
        List<String> items = ImapCodec.parseParenList(sortStr);
        List<SortCriteria> result = new ArrayList<>();
        boolean reverse = false;
        for (String item : items) {
            if (item.equalsIgnoreCase("REVERSE")) {
                reverse = true;
            } else {
                try {
                    SortCriteria.SortKey key = SortCriteria.SortKey.parse(item);
                    result.add(new SortCriteria(key, reverse));
                    reverse = false;
                } catch (IllegalArgumentException e) {
                    // Skip unknown sort keys
                }
            }
        }
        return result;
    }

    private String[] parseLoginArgs(String args) {
        if (args == null || args.isEmpty()) return null;
        List<String> result = new ArrayList<>();
        int pos = 0;
        while (pos < args.length() && result.size() < 2) {
            while (pos < args.length() && args.charAt(pos) == ' ') pos++;
            if (pos >= args.length()) break;

            if (args.charAt(pos) == '"') {
                int end = args.indexOf('"', pos + 1);
                if (end < 0) end = args.length();
                result.add(args.substring(pos + 1, end));
                pos = end + 1;
            } else {
                int end = args.indexOf(' ', pos);
                if (end < 0) end = args.length();
                result.add(args.substring(pos, end));
                pos = end + 1;
            }
        }
        return result.toArray(new String[0]);
    }

    private String unquoteArg(String arg) {
        if (arg == null) return null;
        String trimmed = arg.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String[] splitArgs(String args, int limit) {
        List<String> result = new ArrayList<>();
        int pos = 0;
        while (pos < args.length() && result.size() < limit) {
            while (pos < args.length() && args.charAt(pos) == ' ') pos++;
            if (pos >= args.length()) break;

            if (args.charAt(pos) == '"') {
                int end = args.indexOf('"', pos + 1);
                if (end < 0) end = args.length();
                result.add("\"" + args.substring(pos + 1, end) + "\"");
                pos = end + 1;
            } else {
                int end = args.indexOf(' ', pos);
                if (end < 0) end = args.length();
                result.add(args.substring(pos, end));
                pos = end + 1;
            }
        }
        return result.toArray(new String[0]);
    }

    /** Returns the current session state. */
    public ImapState state() { return state; }

    /** Returns the authenticated user, or null. */
    public String authenticatedUser() { return authenticatedUser; }

    /** Returns the selected mailbox, or null. */
    public Mailbox selectedMailbox() { return selectedMailbox; }

    /** Returns true if the session is running. */
    public boolean isRunning() { return running.get(); }

    @Override
    public void close() {
        running.set(false);
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOG.debug("Error closing session socket", e);
        }
    }
}
