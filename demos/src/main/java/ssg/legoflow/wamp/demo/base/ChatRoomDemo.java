package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.realm.Realm;
import ssg.legoflow.wamp.core.role.Publisher;
import ssg.legoflow.wamp.core.role.Subscriber;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * Chat room demo using WAMP pub/sub with multiple subscribers, join/leave notifications,
 * and message broadcasting across topics within a realm.
 *
 * @since 0.1.0
 */
public class ChatRoomDemo {

    private final Realm realm;
    private final Map<String, UserSession> users = new ConcurrentHashMap<>();

    /**
     * Creates a chat room demo in the given realm.
     *
     * @param realm the WAMP realm for this chat room
     */
    public ChatRoomDemo(Realm realm) {
        this.realm = realm;
    }

    /**
     * A user session holding the subscriber, publisher, transports, and received messages.
     */
    public record UserSession(
            String username,
            Subscriber subscriber,
            Publisher publisher,
            InMemoryTransport subscriberTransport,
            InMemoryTransport subRouterTransport,
            InMemoryTransport pubRouterTransport,
            List<List<Object>> receivedMessages
    ) {}

    /**
     * Adds a user to the chat room, subscribing them to the chat topic
     * and the system notifications topic.
     *
     * @param username the user's display name
     * @return the created user session
     */
    public UserSession join(String username) {
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();
        var broker = realm.getBroker();

        var subscriber = new Subscriber(subPair[0]);
        var publisher = new Publisher(pubPair[0]);
        var messages = new CopyOnWriteArrayList<List<Object>>();

        subscriber.onEvent(e -> messages.add(List.copyOf(e.args())));

        subscribeToTopic(subscriber, subPair, broker, "chat.messages");
        subscribeToTopic(subscriber, subPair, broker, "chat.system");

        var session = new UserSession(username, subscriber, publisher,
                subPair[0], subPair[1], pubPair[1], messages);
        users.put(username, session);

        broadcastSystem(List.of("system", username + " joined the room"));

        return session;
    }

    /**
     * Removes a user from the chat room and broadcasts a leave notification.
     *
     * @param username the user to remove
     */
    public void leave(String username) {
        var session = users.remove(username);
        if (session != null) {
            broadcastSystem(List.of("system", username + " left the room"));
        }
    }

    /**
     * Sends a chat message from the specified user, delivering it to all subscribers.
     *
     * @param username the sender
     * @param text     the message text
     */
    public void sendMessage(String username, String text) {
        var sender = users.get(username);
        if (sender == null) return;

        sender.publisher().publish("chat.messages", List.of(username, text));
        var pubMsg = (WampMessage.Publish) sender.pubRouterTransport().receive();
        realm.getBroker().handlePublish(pubMsg, sender.pubRouterTransport());

        deliverPendingEvents();
    }

    /**
     * Returns the messages received by a specific user.
     *
     * @param username the user whose messages to retrieve
     * @return unmodifiable list of received messages
     */
    public List<List<Object>> getMessages(String username) {
        var session = users.get(username);
        if (session == null) return List.of();
        return Collections.unmodifiableList(session.receivedMessages());
    }

    /**
     * Returns the current list of connected usernames.
     *
     * @return list of usernames
     */
    public List<String> getConnectedUsers() {
        return List.copyOf(users.keySet());
    }

    private void subscribeToTopic(Subscriber subscriber, InMemoryTransport[] subPair,
                                  ssg.legoflow.wamp.core.router.Broker broker,
                                  String topic) {
        subscriber.subscribe(topic);
        var subMsg = (WampMessage.Subscribe) subPair[1].receive();
        subPair[1].send(broker.handleSubscribe(subMsg, subPair[1]));
        subscriber.handleSubscribed((WampMessage.Subscribed) subPair[0].receive());
    }

    private void broadcastSystem(List<Object> payload) {
        var anyUser = users.values().stream().findFirst().orElse(null);
        if (anyUser == null) return;

        anyUser.publisher().publish("chat.system", payload);
        var pubMsg = (WampMessage.Publish) anyUser.pubRouterTransport().receive();
        realm.getBroker().handlePublish(pubMsg, anyUser.pubRouterTransport());

        deliverPendingEvents();
    }

    private void deliverPendingEvents() {
        for (var session : users.values()) {
            WampMessage event;
            while ((event = session.subscriberTransport().tryReceive()) != null) {
                if (event instanceof WampMessage.Event || event instanceof WampMessage.Publish) {
                    session.subscriber().handleEventMessage(event);
                }
            }
        }
    }
}
