package ssg.legoflow.database.postgresql.server;

import ssg.legoflow.database.postgresql.protocol.BackendMessage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * Manages LISTEN/NOTIFY pub/sub channels.
 *
 * @since 0.1.0
 */
public final class NotificationManager {

    private final Map<String, Set<Consumer<BackendMessage.NotificationResponse>>> listeners =
            new ConcurrentHashMap<>();

    /**
     * Creates a new notification manager.
     */
    public NotificationManager() {}

    /**
     * Subscribes a listener to a channel.
     *
     * @param channel  the channel name
     * @param listener the notification listener
     */
    public void listen(String channel, Consumer<BackendMessage.NotificationResponse> listener) {
        listeners.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(listener);
    }

    /**
     * Unsubscribes a listener from a channel.
     *
     * @param channel  the channel name
     * @param listener the notification listener
     */
    public void unlisten(String channel, Consumer<BackendMessage.NotificationResponse> listener) {
        Set<Consumer<BackendMessage.NotificationResponse>> set = listeners.get(channel);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                listeners.remove(channel);
            }
        }
    }

    /**
     * Unsubscribes a listener from all channels.
     *
     * @param listener the notification listener
     */
    public void unlistenAll(Consumer<BackendMessage.NotificationResponse> listener) {
        listeners.values().forEach(set -> set.remove(listener));
        listeners.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * Sends a notification on a channel.
     *
     * @param processId the notifying process ID
     * @param channel   the channel name
     * @param payload   the notification payload
     */
    public void notify(int processId, String channel, String payload) {
        Set<Consumer<BackendMessage.NotificationResponse>> set = listeners.get(channel);
        if (set != null) {
            var notification = new BackendMessage.NotificationResponse(processId, channel, payload);
            for (var listener : set) {
                listener.accept(notification);
            }
        }
    }

    /**
     * Returns the number of channels with active listeners.
     *
     * @return the channel count
     */
    public int channelCount() {
        return listeners.size();
    }
}
