package ssg.legoflow.email.smtp.server;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * In-memory message store for testing and development.
 *
 * <p>Stores all delivered messages in a thread-safe list. Messages are retained
 * in memory until explicitly cleared or the store is garbage collected.
 *
 * @since 0.1.0
 */
public final class InMemoryMessageStore implements MessageStore {

    private final CopyOnWriteArrayList<MailEnvelope> messages = new CopyOnWriteArrayList<>();

    @Override
    public StoreResult store(MailEnvelope envelope) {
        messages.add(envelope);
        return StoreResult.success(envelope.messageId());
    }

    @Override
    public List<MailEnvelope> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    @Override
    public List<MailEnvelope> getMessagesFor(String recipient) {
        return messages.stream()
                .filter(env -> env.recipients().stream()
                        .anyMatch(r -> r.equalsIgnoreCase(recipient)))
                .toList();
    }

    @Override
    public int getMessageCount() {
        return messages.size();
    }

    @Override
    public void clear() {
        messages.clear();
    }

    /**
     * Returns the most recently stored message.
     *
     * @return the last message, or {@code null} if empty
     */
    public MailEnvelope getLastMessage() {
        if (messages.isEmpty()) {
            return null;
        }
        return messages.getLast();
    }
}
