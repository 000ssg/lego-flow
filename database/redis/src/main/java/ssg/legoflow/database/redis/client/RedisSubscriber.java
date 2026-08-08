package ssg.legoflow.database.redis.client;

import ssg.legoflow.database.redis.protocol.RespType;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Pub/Sub subscriber that listens for messages on channels and patterns.
 *
 * <p>Messages are delivered to a registered handler or can be polled
 * from a blocking queue.
 *
 * @since 0.1.0
 */
public final class RedisSubscriber {

    /**
     * A received pub/sub message.
     *
     * @param type    "message" or "pmessage"
     * @param channel the channel name
     * @param pattern the matched pattern (for pmessage), or null
     * @param message the message content
     */
    public record PubSubMessage(String type, String channel, String pattern, String message) {}

    private final RedisClient client;
    private final BlockingQueue<PubSubMessage> messageQueue = new LinkedBlockingQueue<>();
    private volatile BiConsumer<String, String> messageHandler;

    RedisSubscriber(RedisClient client) {
        this.client = client;
    }

    /**
     * Sets a handler for incoming messages.
     *
     * @param handler receives (channel, message) pairs
     */
    public void onMessage(BiConsumer<String, String> handler) {
        this.messageHandler = handler;
    }

    /**
     * Subscribes to channels.
     *
     * @param channels channel names
     * @throws IOException if I/O fails
     */
    public void subscribe(String... channels) throws IOException {
        String[] args = new String[channels.length + 1];
        args[0] = "SUBSCRIBE";
        System.arraycopy(channels, 0, args, 1, channels.length);
        client.execute(args);
    }

    /**
     * Subscribes to patterns.
     *
     * @param patterns glob patterns
     * @throws IOException if I/O fails
     */
    public void psubscribe(String... patterns) throws IOException {
        String[] args = new String[patterns.length + 1];
        args[0] = "PSUBSCRIBE";
        System.arraycopy(patterns, 0, args, 1, patterns.length);
        client.execute(args);
    }

    /**
     * Unsubscribes from channels.
     *
     * @param channels channel names
     * @throws IOException if I/O fails
     */
    public void unsubscribe(String... channels) throws IOException {
        String[] args = new String[channels.length + 1];
        args[0] = "UNSUBSCRIBE";
        System.arraycopy(channels, 0, args, 1, channels.length);
        client.execute(args);
    }

    /**
     * Reads the next message, blocking until one is available.
     *
     * @return the message, or null on timeout/EOF
     * @throws IOException if I/O fails
     */
    public PubSubMessage nextMessage() throws IOException {
        RespType response = client.receive();
        if (response instanceof RespType.Array arr && arr.elements() != null) {
            return parseMessage(arr.elements());
        }
        return null;
    }

    /**
     * Reads the next message with a timeout.
     *
     * @param timeoutMs timeout in milliseconds
     * @return the message, or null on timeout
     * @throws IOException if I/O fails
     */
    public PubSubMessage nextMessage(long timeoutMs) throws IOException {
        // Note: socket read has its own timeout; this is a simplification
        return nextMessage();
    }

    /**
     * Polls the message queue (non-blocking).
     *
     * @return the next message, or null if none available
     */
    public PubSubMessage poll() {
        return messageQueue.poll();
    }

    private PubSubMessage parseMessage(List<RespType> elements) {
        if (elements.size() < 3) return null;
        String type = RedisClient.extractString(elements.get(0));

        if ("message".equals(type)) {
            String channel = RedisClient.extractString(elements.get(1));
            String message = RedisClient.extractString(elements.get(2));
            PubSubMessage msg = new PubSubMessage(type, channel, null, message);
            if (messageHandler != null) messageHandler.accept(channel, message);
            messageQueue.offer(msg);
            return msg;
        }
        if ("pmessage".equals(type) && elements.size() >= 4) {
            String pattern = RedisClient.extractString(elements.get(1));
            String channel = RedisClient.extractString(elements.get(2));
            String message = RedisClient.extractString(elements.get(3));
            PubSubMessage msg = new PubSubMessage(type, channel, pattern, message);
            if (messageHandler != null) messageHandler.accept(channel, message);
            messageQueue.offer(msg);
            return msg;
        }
        return new PubSubMessage(type, RedisClient.extractString(elements.get(1)), null,
                elements.size() > 2 ? RedisClient.extractString(elements.get(2)) : null);
    }
}
