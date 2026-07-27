package ssg.legoflow.rpc.graphql.demo;

import ssg.legoflow.rpc.graphql.execution.SubscriptionPublisher;
import ssg.legoflow.rpc.graphql.schema.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chat application schema with subscriptions.
 *
 * <p>Demonstrates GraphQL subscriptions with a simple chat room
 * that supports sending messages and subscribing to new messages.
 *
 * @since 1.0.0
 */
public final class ChatSchema {

    private final List<Map<String, Object>> messages = new CopyOnWriteArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final SubscriptionPublisher<Map<String, Object>> messagePublisher = new SubscriptionPublisher<>();

    /**
     * Creates a new ChatSchema instance.
     */
    public ChatSchema() {}

    /**
     * Creates the Chat GraphQL schema.
     *
     * @return the schema
     */
    public GraphQLSchema create() {
        var messageType = ObjectType.of("Message", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("text", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("sender", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("timestamp", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("room", NonNullType.of(ScalarType.STRING))
        ));

        var messageInput = InputObjectType.of("MessageInput", List.of(
                InputObjectType.InputFieldDefinition.of("text", NonNullType.of(ScalarType.STRING)),
                InputObjectType.InputFieldDefinition.of("sender", NonNullType.of(ScalarType.STRING)),
                InputObjectType.InputFieldDefinition.of("room", ScalarType.STRING, "general")
        ));

        // Query
        var messagesField = FieldDefinition.of("messages", NonNullType.of(ListType.of(NonNullType.of(messageType))),
                List.of(ArgumentDefinition.of("room", ScalarType.STRING, "general"),
                        ArgumentDefinition.of("limit", ScalarType.INT, 50)));
        messagesField.dataFetcher(env -> {
            String room = env.getArgument("room");
            int limit = env.getArgument("limit") instanceof Number n ? n.intValue() : 50;
            return messages.stream()
                    .filter(m -> room == null || room.equals(m.get("room")))
                    .limit(limit)
                    .toList();
        });

        var messageCountField = FieldDefinition.of("messageCount", NonNullType.of(ScalarType.INT),
                List.of(ArgumentDefinition.of("room", ScalarType.STRING)));
        messageCountField.dataFetcher(env -> {
            String room = env.getArgument("room");
            if (room == null) return messages.size();
            return (int) messages.stream()
                    .filter(m -> room.equals(m.get("room"))).count();
        });

        var queryType = ObjectType.of("Query", List.of(messagesField, messageCountField));

        // Mutation
        var sendMessageField = FieldDefinition.of("sendMessage", NonNullType.of(messageType),
                List.of(ArgumentDefinition.of("input", NonNullType.of(messageInput))));
        sendMessageField.dataFetcher(env -> {
            @SuppressWarnings("unchecked")
            var input = (Map<String, Object>) env.getArgument("input");
            var message = new LinkedHashMap<String, Object>();
            message.put("id", idCounter.getAndIncrement());
            message.put("text", input.get("text"));
            message.put("sender", input.get("sender"));
            message.put("room", input.getOrDefault("room", "general"));
            message.put("timestamp", Instant.now().toString());
            messages.add(message);
            messagePublisher.publish(message);
            return message;
        });

        var mutationType = ObjectType.of("Mutation", List.of(sendMessageField));

        // Subscription
        var newMessageField = FieldDefinition.of("newMessage", NonNullType.of(messageType),
                List.of(ArgumentDefinition.of("room", ScalarType.STRING)));
        newMessageField.dataFetcher(env -> {
            // In a real implementation, this would return a publisher
            // For demo purposes, return the publisher reference
            return messagePublisher;
        });

        var subscriptionType = ObjectType.of("Subscription", List.of(newMessageField));

        return GraphQLSchema.newSchema()
                .query(queryType)
                .mutation(mutationType)
                .subscription(subscriptionType)
                .additionalType(messageInput)
                .build();
    }

    /**
     * Returns the message publisher for subscription integration.
     *
     * @return the publisher
     */
    public SubscriptionPublisher<Map<String, Object>> messagePublisher() {
        return messagePublisher;
    }

    /**
     * Returns all messages (for testing).
     *
     * @return the messages list
     */
    public List<Map<String, Object>> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Sends a message programmatically (for testing).
     *
     * @param text   the message text
     * @param sender the sender name
     * @param room   the room name
     * @return the message data
     */
    public Map<String, Object> sendMessage(String text, String sender, String room) {
        var message = new LinkedHashMap<String, Object>();
        message.put("id", idCounter.getAndIncrement());
        message.put("text", text);
        message.put("sender", sender);
        message.put("room", room != null ? room : "general");
        message.put("timestamp", Instant.now().toString());
        messages.add(message);
        messagePublisher.publish(message);
        return message;
    }
}
