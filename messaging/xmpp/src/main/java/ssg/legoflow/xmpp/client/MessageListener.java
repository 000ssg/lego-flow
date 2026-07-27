package ssg.legoflow.xmpp.client;

import ssg.legoflow.xmpp.core.MessageStanza;

/**
 * Functional interface for receiving XMPP message events.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface MessageListener {

    /**
     * Called when a message is received.
     *
     * @param message the received message stanza
     */
    void onMessage(MessageStanza message);
}
