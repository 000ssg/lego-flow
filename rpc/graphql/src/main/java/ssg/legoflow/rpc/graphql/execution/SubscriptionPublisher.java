package ssg.legoflow.rpc.graphql.execution;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;

/**
 * Async publisher for GraphQL subscription events.
 *
 * <p>Wraps a {@link SubmissionPublisher} to provide a simple API for
 * publishing subscription events and managing subscribers.
 *
 * @param <T> the event type
 * @since 1.0.0
 */
public final class SubscriptionPublisher<T> implements AutoCloseable {

    private final SubmissionPublisher<T> publisher;
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new subscription publisher.
     */
    public SubscriptionPublisher() {
        this.publisher = new SubmissionPublisher<>();
    }

    /**
     * Publishes an event to all subscribers.
     *
     * @param event the event to publish
     */
    public void publish(T event) {
        publisher.submit(event);
        for (var listener : listeners) {
            listener.accept(event);
        }
    }

    /**
     * Subscribes to events with a Flow.Subscriber.
     *
     * @param subscriber the subscriber
     */
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        publisher.subscribe(subscriber);
    }

    /**
     * Subscribes to events with a simple consumer callback.
     *
     * @param listener the event listener
     * @return a runnable that unsubscribes
     */
    public Runnable subscribe(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * Returns the number of current subscribers.
     *
     * @return the subscriber count
     */
    public int subscriberCount() {
        return publisher.getNumberOfSubscribers() + listeners.size();
    }

    /**
     * Returns whether this publisher has been closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return publisher.isClosed();
    }

    @Override
    public void close() {
        publisher.close();
        listeners.clear();
    }
}
