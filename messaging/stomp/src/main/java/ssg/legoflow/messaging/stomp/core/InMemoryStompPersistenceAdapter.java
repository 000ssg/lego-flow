package ssg.legoflow.messaging.stomp.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-memory reference implementation of {@link StompPersistenceAdapter}.
 *
 * <p>Thread-safe, suitable for testing and prototyping. Data is lost on process exit.</p>
 *
 * @since 0.2.0
 */
public class InMemoryStompPersistenceAdapter implements StompPersistenceAdapter {

    private final Map<String, StompSessionData> sessions = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedQueue<StompFrame>> queuedMessages = new ConcurrentHashMap<>();

    @Override
    public void saveSession(StompSessionData data) {
        sessions.put(data.sessionId(), data);
    }

    @Override
    public Optional<StompSessionData> loadSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<StompSessionData> loadAllSessions() {
        return new ArrayList<>(sessions.values());
    }

    @Override
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public void queueMessage(String destination, StompFrame frame) {
        queuedMessages.computeIfAbsent(destination, k -> new ConcurrentLinkedQueue<>())
                .add(frame);
    }

    @Override
    public List<StompFrame> loadQueuedMessages(String destination) {
        var queue = queuedMessages.get(destination);
        return queue == null ? Collections.emptyList() : new ArrayList<>(queue);
    }

    @Override
    public void removeFromQueue(String messageId) {
        // Scan all queues for the message
        for (var queue : queuedMessages.values()) {
            queue.removeIf(f -> f.header(StompHeaders.MESSAGE_ID) != null
                    && f.header(StompHeaders.MESSAGE_ID).equals(messageId));
        }
    }

    @Override
    public void close() {
        sessions.clear();
        queuedMessages.clear();
    }
}
