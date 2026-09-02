package ssg.legoflow.database.redis.server;

import ssg.legoflow.database.redis.protocol.RespCodec;
import ssg.legoflow.database.redis.protocol.RespType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;
/**
 * Manages pub/sub channel and pattern subscriptions.
 *
 * <p>Routes published messages to all subscribers matching the channel
 * by exact name or by glob pattern.
 *
 * @since 0.1.0
 */
public final class PubSubManager {

    private final Map<String, Set<ClientConnection>> channelSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Set<PatternSubscription>> patternSubscribers = new ConcurrentHashMap<>();

    /**
     * A pattern subscription with the compiled regex.
     */
    record PatternSubscription(String pattern, Pattern regex, ClientConnection client) {}

    /**
     * Subscribes a client to one or more channels.
     *
     * @param client   the client
     * @param channels the channel names
     * @return subscription counts per channel (in order)
     */
    public List<Long> subscribe(ClientConnection client, String... channels) {
        List<Long> counts = new ArrayList<>();
        for (String channel : channels) {
            channelSubscribers.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(client);
            client.subscriptions().add(channel);
            counts.add((long) client.subscriptions().size() + client.patternSubscriptions().size());
        }
        return counts;
    }

    /**
     * Unsubscribes a client from channels.
     *
     * @param client   the client
     * @param channels the channels (empty = unsubscribe all)
     * @return subscription counts per channel (in order)
     */
    public List<Long> unsubscribe(ClientConnection client, String... channels) {
        String[] targets = channels.length > 0 ? channels : client.subscriptions().toArray(String[]::new);
        List<Long> counts = new ArrayList<>();
        for (String channel : targets) {
            Set<ClientConnection> subs = channelSubscribers.get(channel);
            if (subs != null) {
                subs.remove(client);
                if (subs.isEmpty()) channelSubscribers.remove(channel);
            }
            client.subscriptions().remove(channel);
            counts.add((long) client.subscriptions().size() + client.patternSubscriptions().size());
        }
        return counts;
    }

    /**
     * Subscribes a client to one or more patterns.
     *
     * @param client   the client
     * @param patterns glob patterns
     * @return subscription counts per pattern
     */
    public List<Long> psubscribe(ClientConnection client, String... patterns) {
        List<Long> counts = new ArrayList<>();
        for (String pattern : patterns) {
            Pattern regex = Database.globToRegex(pattern);
            patternSubscribers.computeIfAbsent(pattern, k -> new CopyOnWriteArraySet<>())
                    .add(new PatternSubscription(pattern, regex, client));
            client.patternSubscriptions().add(pattern);
            counts.add((long) client.subscriptions().size() + client.patternSubscriptions().size());
        }
        return counts;
    }

    /**
     * Unsubscribes a client from patterns.
     *
     * @param client   the client
     * @param patterns the patterns (empty = unsubscribe all)
     * @return subscription counts per pattern
     */
    public List<Long> punsubscribe(ClientConnection client, String... patterns) {
        String[] targets = patterns.length > 0 ? patterns : client.patternSubscriptions().toArray(String[]::new);
        List<Long> counts = new ArrayList<>();
        for (String pattern : targets) {
            Set<PatternSubscription> subs = patternSubscribers.get(pattern);
            if (subs != null) {
                subs.removeIf(ps -> ps.client() == client);
                if (subs.isEmpty()) patternSubscribers.remove(pattern);
            }
            client.patternSubscriptions().remove(pattern);
            counts.add((long) client.subscriptions().size() + client.patternSubscriptions().size());
        }
        return counts;
    }

    /**
     * Publishes a message to a channel.
     *
     * @param channel the channel name
     * @param message the message
     * @return number of clients that received the message
     */
    public long publish(String channel, String message) {
        long count = 0;

        // Exact channel subscribers
        Set<ClientConnection> subs = channelSubscribers.get(channel);
        if (subs != null) {
            for (ClientConnection client : subs) {
                sendMessage(client, "message", channel, null, message);
                count++;
            }
        }

        // Pattern subscribers
        for (var entry : patternSubscribers.entrySet()) {
            for (PatternSubscription ps : entry.getValue()) {
                if (ps.regex().matcher(channel).matches()) {
                    sendMessage(ps.client(), "pmessage", channel, ps.pattern(), message);
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Returns the number of subscribers for the given channels.
     *
     * @param channels channel names
     * @return map of channel to subscriber count
     */
    public Map<String, Long> numsub(String... channels) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String channel : channels) {
            Set<ClientConnection> subs = channelSubscribers.get(channel);
            result.put(channel, subs == null ? 0L : (long) subs.size());
        }
        return result;
    }

    /**
     * Returns the number of pattern subscriptions.
     *
     * @return total pattern count
     */
    public long numpat() {
        return patternSubscribers.values().stream().mapToLong(Set::size).sum();
    }

    /**
     * Returns active channel names.
     *
     * @param pattern optional glob filter (null for all)
     * @return matching channel names
     */
    public List<String> channels(String pattern) {
        if (pattern == null) {
            return new ArrayList<>(channelSubscribers.keySet());
        }
        Pattern regex = Database.globToRegex(pattern);
        List<String> result = new ArrayList<>();
        for (String ch : channelSubscribers.keySet()) {
            if (regex.matcher(ch).matches()) {
                result.add(ch);
            }
        }
        return result;
    }

    /**
     * Removes all subscriptions for a client.
     *
     * @param client the client
     */
    public void removeClient(ClientConnection client) {
        unsubscribe(client);
        punsubscribe(client);
    }

    private void sendMessage(ClientConnection client, String type, String channel,
                             String pattern, String message) {
        List<RespType> elements = new ArrayList<>();
        elements.add(RespType.BulkString.of(type));
        if (pattern != null) {
            elements.add(RespType.BulkString.of(pattern));
        }
        elements.add(RespType.BulkString.of(channel));
        elements.add(RespType.BulkString.of(message));

        byte[] data = RespCodec.encode(new RespType.Array(elements));
        client.writeRaw(data);
    }
}
