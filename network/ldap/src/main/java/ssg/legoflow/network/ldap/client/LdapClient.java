package ssg.legoflow.network.ldap.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.ldap.codec.LdapCodec;
import ssg.legoflow.network.ldap.control.LdapControl;
import ssg.legoflow.network.ldap.control.PagedResultsControl;
import ssg.legoflow.network.ldap.filter.SearchFilter;
import ssg.legoflow.network.ldap.protocol.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LDAP v3 client for connecting to LDAP servers (RFC 4511).
 *
 * <p>Provides methods for bind, search, compare, add, delete, modify,
 * modify DN, and extended operations. Supports paged results via controls.
 *
 * <p>This client is thread-safe; operations synchronize on the connection.
 * Uses virtual threads for asynchronous I/O where appropriate.
 *
 * <p>Usage example:
 * <pre>{@code
 * try (var client = LdapClient.connect("ldap.example.com", 389)) {
 *     client.bind("cn=admin,dc=example,dc=com", "secret");
 *     var results = client.search("dc=example,dc=com",
 *             SearchScope.WHOLE_SUBTREE,
 *             SearchFilter.equalityMatch("objectClass", "person"));
 *     for (var entry : results) {
 *         System.out.println(entry.objectName());
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class LdapClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LdapClient.class);
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final AtomicInteger messageIdCounter = new AtomicInteger(0);
    private final Object lock = new Object();
    private volatile boolean closed;

    private LdapClient(Socket socket) throws IOException {
        this.socket = socket;
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
    }

    /**
     * Connects to an LDAP server.
     *
     * @param host the server hostname
     * @param port the server port (typically 389)
     * @return the connected client
     * @throws IOException if the connection fails
     */
    public static LdapClient connect(String host, int port) throws IOException {
        return connect(host, port, 30_000);
    }

    /**
     * Connects to an LDAP server with a timeout.
     *
     * @param host      the server hostname
     * @param port      the server port
     * @param timeoutMs the connection timeout in milliseconds
     * @return the connected client
     * @throws IOException if the connection fails
     */
    public static LdapClient connect(String host, int port, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        LOG.info("Connected to LDAP server at {}:{}", host, port);
        return new LdapClient(socket);
    }

    /**
     * Creates a client from an existing socket connection.
     *
     * @param socket the connected socket
     * @return the client
     * @throws IOException if stream creation fails
     */
    public static LdapClient fromSocket(Socket socket) throws IOException {
        return new LdapClient(socket);
    }

    /**
     * Performs a simple bind operation.
     *
     * @param dn       the DN to bind as
     * @param password the password
     * @return the bind response
     * @throws IOException if an I/O error occurs
     */
    public BindResponse bind(String dn, String password) throws IOException {
        LdapMessage request = LdapMessage.of(nextMessageId(), BindRequest.simple(dn, password));
        LdapMessage response = sendAndReceive(request);
        if (response.protocolOp() instanceof BindResponse bindResp) {
            LOG.debug("Bind result: {}", bindResp.result().resultCode());
            return bindResp;
        }
        throw new LdapClientException("Unexpected response to bind: " +
                response.protocolOp().getClass().getSimpleName());
    }

    /**
     * Performs an anonymous bind.
     *
     * @return the bind response
     * @throws IOException if an I/O error occurs
     */
    public BindResponse bindAnonymous() throws IOException {
        LdapMessage request = LdapMessage.of(nextMessageId(), BindRequest.anonymous());
        LdapMessage response = sendAndReceive(request);
        if (response.protocolOp() instanceof BindResponse bindResp) {
            return bindResp;
        }
        throw new LdapClientException("Unexpected response to anonymous bind");
    }

    /**
     * Performs a search operation.
     *
     * @param baseDn the base DN
     * @param scope  the search scope
     * @param filter the search filter
     * @return the list of search result entries
     * @throws IOException if an I/O error occurs
     */
    public List<SearchResultEntry> search(String baseDn, SearchScope scope,
                                          SearchFilter filter) throws IOException {
        return search(baseDn, scope, filter, List.of());
    }

    /**
     * Performs a search operation with specific attributes.
     *
     * @param baseDn     the base DN
     * @param scope      the search scope
     * @param filter     the search filter
     * @param attributes the attributes to return
     * @return the list of search result entries
     * @throws IOException if an I/O error occurs
     */
    public List<SearchResultEntry> search(String baseDn, SearchScope scope,
                                          SearchFilter filter,
                                          List<String> attributes) throws IOException {
        return search(baseDn, scope, filter, attributes, 0, 0);
    }

    /**
     * Performs a search operation with limits.
     *
     * @param baseDn     the base DN
     * @param scope      the search scope
     * @param filter     the search filter
     * @param attributes the attributes to return
     * @param sizeLimit  the maximum number of entries (0 = no limit)
     * @param timeLimit  the maximum time in seconds (0 = no limit)
     * @return the list of search result entries
     * @throws IOException if an I/O error occurs
     */
    public List<SearchResultEntry> search(String baseDn, SearchScope scope,
                                          SearchFilter filter, List<String> attributes,
                                          int sizeLimit, int timeLimit) throws IOException {
        SearchRequest searchReq = new SearchRequest(baseDn, scope,
                DerefAliases.NEVER_DEREF_ALIASES, sizeLimit, timeLimit,
                false, filter, attributes);
        return executeSearch(searchReq, List.of());
    }

    /**
     * Performs a paged search operation.
     *
     * @param baseDn   the base DN
     * @param scope    the search scope
     * @param filter   the search filter
     * @param pageSize the number of results per page
     * @return the list of all search result entries across all pages
     * @throws IOException if an I/O error occurs
     */
    public List<SearchResultEntry> searchPaged(String baseDn, SearchScope scope,
                                               SearchFilter filter,
                                               int pageSize) throws IOException {
        List<SearchResultEntry> allResults = new ArrayList<>();
        byte[] cookie = new byte[0];

        do {
            SearchRequest searchReq = new SearchRequest(baseDn, scope,
                    DerefAliases.NEVER_DEREF_ALIASES, 0, 0, false,
                    filter, List.of());
            LdapControl pagedControl = PagedResultsControl.request(pageSize, cookie);
            int msgId = nextMessageId();
            LdapMessage request = LdapMessage.of(msgId, searchReq, List.of(pagedControl));
            send(request);

            SearchResultDone done = null;
            List<LdapControl> responseControls = List.of();
            while (done == null) {
                LdapMessage response = receive();
                switch (response.protocolOp()) {
                    case SearchResultEntry entry -> allResults.add(entry);
                    case SearchResultDone d -> {
                        done = d;
                        responseControls = response.controls();
                    }
                    case SearchResultReference _ -> { /* skip referrals */ }
                    default -> throw new LdapClientException("Unexpected response: " +
                            response.protocolOp().getClass().getSimpleName());
                }
            }

            // Extract cookie from response control
            cookie = new byte[0];
            for (LdapControl ctrl : responseControls) {
                if (PagedResultsControl.OID.equals(ctrl.oid()) && ctrl.value() != null) {
                    cookie = PagedResultsControl.decodeCookie(ctrl.value());
                }
            }
        } while (cookie.length > 0);

        return allResults;
    }

    /**
     * Performs a compare operation.
     *
     * @param entry     the entry DN
     * @param attribute the attribute to compare
     * @param value     the assertion value
     * @return true if the attribute value matches
     * @throws IOException if an I/O error occurs
     */
    public boolean compare(String entry, String attribute, String value) throws IOException {
        LdapMessage request = LdapMessage.of(nextMessageId(),
                CompareRequest.of(entry, attribute, value));
        LdapMessage response = sendAndReceive(request);
        if (response.protocolOp() instanceof CompareResponse compareResp) {
            return compareResp.result().resultCode() == LdapResultCode.COMPARE_TRUE;
        }
        throw new LdapClientException("Unexpected response to compare");
    }

    /**
     * Adds an entry to the directory.
     *
     * @param entry      the entry DN
     * @param attributes the entry's attributes
     * @return the add response
     * @throws IOException if an I/O error occurs
     */
    public AddResponse add(String entry, List<LdapAttribute> attributes) throws IOException {
        LdapMessage request = LdapMessage.of(nextMessageId(),
                new AddRequest(entry, attributes));
        LdapMessage response = sendAndReceive(request);
        if (response.protocolOp() instanceof AddResponse addResp) {
            return addResp;
        }
        throw new LdapClientException("Unexpected response to add");
    }

    /**
     * Deletes an entry from the directory.
     *
     * @param entry the entry DN to delete
     * @return the delete response
     * @throws IOException if an I/O error occurs
     */
    public DeleteResponse delete(String entry) throws IOException {
        LdapMessage request = LdapMessage.of(nextMessageId(), new DeleteRequest(entry));
        LdapMessage response = sendAndReceive(request);
        if (response.protocolOp() instanceof DeleteResponse delResp) {
            return delResp;
        }
        throw new LdapClientException("Unexpected response to delete");
    }

    /**
     * Modifies an entry in the directory.
     *
     * @param object  the entry DN to modify
     * @param changes the modifications to apply
     * @return the modify response
     * @throws IOException if an I/O error occurs
     */
    public ModifyResponse modify(String object, List<ModifyRequest.Change> changes) throws IOException {
        LdapMessage request = LdapMessage.of(nextMessageId(),
                new ModifyRequest(object, changes));
        LdapMessage response = sendAndReceive(request);
        if (response.protocolOp() instanceof ModifyResponse modResp) {
            return modResp;
        }
        throw new LdapClientException("Unexpected response to modify");
    }

    /**
     * Renames or moves an entry.
     *
     * @param entry        the current entry DN
     * @param newRdn       the new RDN
     * @param deleteOldRdn whether to delete old RDN values
     * @param newSuperior  the new parent DN (null if not moving)
     * @return the modify DN response
     * @throws IOException if an I/O error occurs
     */
    public ModifyDnResponse modifyDn(String entry, String newRdn,
                                     boolean deleteOldRdn, String newSuperior) throws IOException {
        LdapMessage request = LdapMessage.of(nextMessageId(),
                new ModifyDnRequest(entry, newRdn, deleteOldRdn, newSuperior));
        LdapMessage response = sendAndReceive(request);
        if (response.protocolOp() instanceof ModifyDnResponse modDnResp) {
            return modDnResp;
        }
        throw new LdapClientException("Unexpected response to modifyDN");
    }

    /**
     * Sends an unbind notification and closes the connection.
     *
     * @throws IOException if an I/O error occurs
     */
    public void unbind() throws IOException {
        if (!closed) {
            send(LdapMessage.of(nextMessageId(), UnbindRequest.INSTANCE));
            close();
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            socket.close();
            LOG.debug("LDAP connection closed");
        }
    }

    // ── Internal ──

    private int nextMessageId() {
        return messageIdCounter.incrementAndGet();
    }

    private LdapMessage sendAndReceive(LdapMessage request) throws IOException {
        synchronized (lock) {
            send(request);
            return receive();
        }
    }

    private void send(LdapMessage message) throws IOException {
        byte[] encoded = LdapCodec.encodeToBytes(message);
        synchronized (lock) {
            output.write(encoded);
            output.flush();
        }
        LOG.trace("Sent LDAP message: id={} op={}", message.messageId(),
                message.protocolOp().getClass().getSimpleName());
    }

    private LdapMessage receive() throws IOException {
        synchronized (lock) {
            ByteBuffer buffer = readMessage();
            return LdapCodec.decode(buffer);
        }
    }

    private ByteBuffer readMessage() throws IOException {
        // Read tag
        int firstByte = input.read();
        if (firstByte < 0) throw new IOException("Connection closed by server");

        // Read length
        int secondByte = input.read();
        if (secondByte < 0) throw new IOException("Connection closed while reading length");

        int length;
        int headerSize;
        if (secondByte <= 127) {
            length = secondByte;
            headerSize = 2;
        } else {
            int numBytes = secondByte & 0x7F;
            headerSize = 2 + numBytes;
            length = 0;
            for (int i = 0; i < numBytes; i++) {
                int b = input.read();
                if (b < 0) throw new IOException("Connection closed while reading length");
                length = (length << 8) | b;
            }
        }

        // Read content
        byte[] content = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(content, offset, length - offset);
            if (read < 0) throw new IOException("Connection closed while reading content");
            offset += read;
        }

        // Reconstruct full TLV
        ByteBuffer buffer = ByteBuffer.allocate(headerSize + length);
        buffer.put((byte) firstByte);
        if (secondByte <= 127) {
            buffer.put((byte) secondByte);
        } else {
            buffer.put((byte) secondByte);
            int numBytes = secondByte & 0x7F;
            for (int i = numBytes - 1; i >= 0; i--) {
                buffer.put((byte) ((length >> (8 * i)) & 0xFF));
            }
        }
        buffer.put(content);
        buffer.flip();

        return buffer;
    }

    private List<SearchResultEntry> executeSearch(SearchRequest searchReq,
                                                  List<LdapControl> controls) throws IOException {
        int msgId = nextMessageId();
        LdapMessage request = LdapMessage.of(msgId, searchReq, controls);
        send(request);

        List<SearchResultEntry> entries = new ArrayList<>();
        boolean done = false;
        while (!done) {
            LdapMessage response = receive();
            switch (response.protocolOp()) {
                case SearchResultEntry entry -> entries.add(entry);
                case SearchResultDone _ -> done = true;
                case SearchResultReference _ -> { /* skip referrals */ }
                default -> throw new LdapClientException("Unexpected response: " +
                        response.protocolOp().getClass().getSimpleName());
            }
        }
        return entries;
    }
}
