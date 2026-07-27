package ssg.legoflow.database.redis.client;

import ssg.legoflow.database.redis.protocol.RespCodec;
import ssg.legoflow.database.redis.protocol.RespParser;
import ssg.legoflow.database.redis.protocol.RespType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Redis client for connecting to a Redis server.
 *
 * <p>Supports command execution, pipelining, and pub/sub subscriptions.
 * Uses the RESP protocol for wire-format encoding/decoding.
 *
 * @since 1.0.0
 */
public final class RedisClient implements AutoCloseable {

    private final String host;
    private final int port;
    private Socket socket;
    private OutputStream output;
    private RespParser parser;

    /**
     * Creates a client targeting the given host and port.
     *
     * @param host the server host
     * @param port the server port
     */
    public RedisClient(String host, int port) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
    }

    /**
     * Connects to the Redis server.
     *
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        output = socket.getOutputStream();
        parser = new RespParser(socket.getInputStream());
    }

    /**
     * Sends a command and returns the response.
     *
     * @param args command name followed by arguments
     * @return the response
     * @throws IOException if I/O fails
     */
    public RespType execute(String... args) throws IOException {
        byte[] data = RespCodec.encodeCommand(args);
        synchronized (output) {
            output.write(data);
            output.flush();
        }
        return parser.parse();
    }

    /**
     * Sends a command without waiting for a response.
     *
     * @param args command name followed by arguments
     * @throws IOException if I/O fails
     */
    public void send(String... args) throws IOException {
        byte[] data = RespCodec.encodeCommand(args);
        synchronized (output) {
            output.write(data);
            output.flush();
        }
    }

    /**
     * Reads the next response from the server.
     *
     * @return the response
     * @throws IOException if I/O fails
     */
    public RespType receive() throws IOException {
        return parser.parse();
    }

    // ---- Convenience methods ----

    /**
     * SET key value.
     *
     * @param key   the key
     * @param value the value
     * @return "OK" response
     * @throws IOException if I/O fails
     */
    public String set(String key, String value) throws IOException {
        RespType response = execute("SET", key, value);
        return extractString(response);
    }

    /**
     * GET key.
     *
     * @param key the key
     * @return the value, or null
     * @throws IOException if I/O fails
     */
    public String get(String key) throws IOException {
        RespType response = execute("GET", key);
        return extractString(response);
    }

    /**
     * DEL key(s).
     *
     * @param keys the keys to delete
     * @return number of keys deleted
     * @throws IOException if I/O fails
     */
    public long del(String... keys) throws IOException {
        String[] args = new String[keys.length + 1];
        args[0] = "DEL";
        System.arraycopy(keys, 0, args, 1, keys.length);
        RespType response = execute(args);
        return extractLong(response);
    }

    /**
     * PING.
     *
     * @return "PONG"
     * @throws IOException if I/O fails
     */
    public String ping() throws IOException {
        return extractString(execute("PING"));
    }

    /**
     * Creates a pipeline for batching commands.
     *
     * @return a new pipeline
     */
    public RedisPipeline pipeline() {
        return new RedisPipeline(this);
    }

    /**
     * Creates a subscriber for pub/sub.
     *
     * @return a new subscriber
     */
    public RedisSubscriber subscriber() {
        return new RedisSubscriber(this);
    }

    /**
     * Sends raw bytes to the server.
     *
     * @param data the bytes
     * @throws IOException if I/O fails
     */
    void sendRaw(byte[] data) throws IOException {
        synchronized (output) {
            output.write(data);
            output.flush();
        }
    }

    /**
     * Returns the underlying parser.
     *
     * @return the parser
     */
    RespParser parser() {
        return parser;
    }

    /**
     * Returns whether the client is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    // ---- Extraction helpers ----

    /**
     * Extracts a string from a RESP response.
     *
     * @param response the RESP type
     * @return string value, or null
     */
    public static String extractString(RespType response) {
        return switch (response) {
            case RespType.SimpleString ss -> ss.value();
            case RespType.BulkString bs -> bs.asString();
            case RespType.Integer i -> String.valueOf(i.value());
            case RespType.Error err -> throw new RuntimeException("Redis error: " + err.fullMessage());
            case null -> null;
            default -> response.toString();
        };
    }

    /**
     * Extracts a long from a RESP response.
     *
     * @param response the RESP type
     * @return long value
     */
    public static long extractLong(RespType response) {
        return switch (response) {
            case RespType.Integer i -> i.value();
            case RespType.BulkString bs -> Long.parseLong(bs.asString());
            case RespType.Error err -> throw new RuntimeException("Redis error: " + err.fullMessage());
            default -> throw new IllegalArgumentException("Expected integer, got: " + response);
        };
    }

    /**
     * Extracts a list of strings from a RESP array response.
     *
     * @param response the RESP array
     * @return list of strings
     */
    public static List<String> extractStringList(RespType response) {
        if (response instanceof RespType.Array arr && arr.elements() != null) {
            List<String> result = new ArrayList<>();
            for (RespType element : arr.elements()) {
                result.add(extractString(element));
            }
            return result;
        }
        return List.of();
    }
}
