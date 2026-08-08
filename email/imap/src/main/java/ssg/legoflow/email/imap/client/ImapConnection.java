package ssg.legoflow.email.imap.client;

import ssg.legoflow.email.imap.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the TCP connection lifecycle for an IMAP client.
 *
 * <p>Handles connecting, sending commands, reading responses,
 * and capability negotiation. Provides low-level protocol I/O
 * used by {@link ImapClient}.
 *
 * @since 0.1.0
 */
public final class ImapConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ImapConnection.class);

    private final ImapClientConfig config;
    private final ImapTag tagGenerator = new ImapTag();
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected = false;
    private List<String> capabilities = new CopyOnWriteArrayList<>();
    private String greeting;

    /**
     * Creates a connection for the given configuration.
     *
     * @param config the client configuration
     */
    public ImapConnection(ImapClientConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Connects to the IMAP server.
     *
     * @throws IOException if the connection fails
     */
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(config.host(), config.port()),
                (int) config.connectTimeout().toMillis());
        socket.setSoTimeout((int) config.readTimeout().toMillis());

        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        // Read server greeting
        greeting = reader.readLine();
        if (greeting != null) {
            ImapResponse resp = ImapResponse.parse(greeting);
            // Extract capabilities from greeting if present
            if (resp.responseCode() != null && resp.responseCode().startsWith("CAPABILITY")) {
                String capStr = resp.responseCode().substring("CAPABILITY".length()).trim();
                capabilities.addAll(List.of(capStr.split("\\s+")));
            }
        }

        connected = true;
        LOG.debug("Connected to {}:{}", config.host(), config.port());
    }

    /**
     * Sends a command and collects all responses until the tagged response.
     *
     * @param command the command
     * @param args    the command arguments
     * @return the list of responses (untagged + final tagged)
     * @throws IOException if an I/O error occurs
     */
    public List<ImapResponse> executeCommand(ImapCommand command, String... args) throws IOException {
        String tag = tagGenerator.next();
        String line = ImapCodec.encodeCommand(tag, command, args);
        return sendAndCollect(tag, line);
    }

    /**
     * Sends a raw command string and collects responses.
     *
     * @param commandLine the full command line (tag + command + args)
     * @return the list of responses
     * @throws IOException if an I/O error occurs
     */
    public List<ImapResponse> executeRaw(String commandLine) throws IOException {
        String tag = commandLine.split("\\s+")[0];
        writer.print(commandLine);
        if (!commandLine.endsWith("\r\n")) {
            writer.print("\r\n");
        }
        writer.flush();
        return collectResponses(tag);
    }

    /**
     * Sends a command and returns only the tagged (final) response.
     *
     * @param command the command
     * @param args    the arguments
     * @return the tagged response
     * @throws IOException if an I/O error occurs
     */
    public ImapResponse executeForStatus(ImapCommand command, String... args) throws IOException {
        List<ImapResponse> responses = executeCommand(command, args);
        return responses.stream()
                .filter(ImapResponse::isTagged)
                .findFirst()
                .orElseThrow(() -> new IOException("No tagged response received"));
    }

    /**
     * Generates the next tag.
     *
     * @return the next tag
     */
    public String nextTag() {
        return tagGenerator.next();
    }

    /**
     * Sends a raw line to the server.
     *
     * @param line the line to send (CRLF appended automatically)
     */
    public void sendLine(String line) {
        writer.print(line + "\r\n");
        writer.flush();
    }

    /**
     * Reads a single response line from the server.
     *
     * @return the parsed response, or null on EOF
     * @throws IOException if an I/O error occurs
     */
    public ImapResponse readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) return null;
        return ImapResponse.parse(line);
    }

    /**
     * Returns the server greeting.
     *
     * @return the greeting line
     */
    public String greeting() { return greeting; }

    /**
     * Returns the server capabilities.
     *
     * @return the capability list
     */
    public List<String> capabilities() { return Collections.unmodifiableList(capabilities); }

    /**
     * Returns true if the server supports the given capability.
     *
     * @param capability the capability name
     * @return true if supported
     */
    public boolean hasCapability(String capability) {
        return capabilities.stream().anyMatch(c -> c.equalsIgnoreCase(capability));
    }

    /**
     * Updates capabilities from a capability response.
     *
     * @param capLine the capability line
     */
    public void updateCapabilities(String capLine) {
        capabilities.clear();
        capabilities.addAll(List.of(capLine.split("\\s+")));
    }

    /** Returns true if connected. */
    public boolean isConnected() { return connected; }

    /** Returns the underlying reader for IDLE and literal operations. */
    BufferedReader reader() { return reader; }

    private List<ImapResponse> sendAndCollect(String tag, String commandLine) throws IOException {
        writer.print(commandLine);
        writer.flush();
        return collectResponses(tag);
    }

    private List<ImapResponse> collectResponses(String tag) throws IOException {
        List<ImapResponse> responses = new ArrayList<>();
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Connection closed by server");
            }
            ImapResponse response = ImapResponse.parse(line);
            responses.add(response);

            // Update capabilities if we see a CAPABILITY response
            if (response.isUntagged() && response.text() != null
                    && response.text().startsWith("CAPABILITY ")) {
                updateCapabilities(response.text().substring("CAPABILITY ".length()));
            }

            if (response.isTagged() && response.tag().equals(tag)) {
                break;
            }
        }
        return responses;
    }

    @Override
    public void close() throws IOException {
        connected = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
