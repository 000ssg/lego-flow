package ssg.legoflow.wamp.core;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing all WAMP protocol messages.
 * Each message type is modeled as a record implementing this interface.
 *
 * @since 0.1.0
 */
public sealed interface WampMessage {

    /**
     * Returns the WAMP message type of this message.
     *
     * @return the message type
     */
    WampMessageType type();

    // --- Session lifecycle ---

    /**
     * Client-to-router: initiates a WAMP session within a realm.
     *
     * @param realm   the realm to join
     * @param details client role and feature details
     */
    record Hello(String realm, Map<String, Object> details) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.HELLO; }
    }

    /**
     * Router-to-client: confirms session establishment.
     *
     * @param sessionId the assigned session identifier
     * @param details   router role and feature details
     */
    record Welcome(long sessionId, Map<String, Object> details) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.WELCOME; }
    }

    /**
     * Router-to-client or client-to-router: aborts session establishment.
     *
     * @param details additional abort details
     * @param reason  the abort reason URI
     */
    record Abort(Map<String, Object> details, String reason) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.ABORT; }
    }

    /**
     * Bidirectional: graceful session close.
     *
     * @param details additional goodbye details
     * @param reason  the goodbye reason URI
     */
    record Goodbye(Map<String, Object> details, String reason) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.GOODBYE; }
    }

    // --- Authentication ---

    /**
     * Router-to-client: challenges client during authentication.
     *
     * @param authMethod the authentication method (e.g. "wampcra", "ticket", "cryptosign")
     * @param extra      additional challenge data
     * @since 0.1.0
     */
    record Challenge(String authMethod, Map<String, Object> extra) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.CHALLENGE; }
    }

    /**
     * Client-to-router: responds to an authentication challenge.
     *
     * @param signature the authentication signature/token
     * @param extra     additional authentication data
     * @since 0.1.0
     */
    record Authenticate(String signature, Map<String, Object> extra) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.AUTHENTICATE; }
    }

    // --- Error ---

    /**
     * Router-to-client: error response to a prior request.
     *
     * @param requestType the type code of the original request
     * @param requestId   the request ID of the original request
     * @param details     error details
     * @param error       the error URI
     */
    record Error(int requestType, long requestId, Map<String, Object> details, String error) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.ERROR; }
    }

    // --- Publish / Subscribe ---

    /**
     * Client-to-router: publishes an event to a topic.
     *
     * @param requestId the request identifier
     * @param options   publish options
     * @param topic     the topic URI
     * @param args      positional arguments
     */
    record Publish(long requestId, Map<String, Object> options, String topic, List<Object> args) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.PUBLISH; }
    }

    /**
     * Router-to-client: acknowledges a publication.
     *
     * @param requestId     the original publish request ID
     * @param publicationId the assigned publication identifier
     */
    record Published(long requestId, long publicationId) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.PUBLISHED; }
    }

    /**
     * Client-to-router: subscribes to a topic.
     *
     * @param requestId the request identifier
     * @param options   subscribe options
     * @param topic     the topic URI
     */
    record Subscribe(long requestId, Map<String, Object> options, String topic) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.SUBSCRIBE; }
    }

    /**
     * Router-to-client: acknowledges a subscription.
     *
     * @param requestId      the original subscribe request ID
     * @param subscriptionId the assigned subscription identifier
     */
    record Subscribed(long requestId, long subscriptionId) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.SUBSCRIBED; }
    }

    /**
     * Client-to-router: unsubscribes from a topic.
     *
     * @param requestId      the request identifier
     * @param subscriptionId the subscription to cancel
     */
    record Unsubscribe(long requestId, long subscriptionId) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.UNSUBSCRIBE; }
    }

    /**
     * Router-to-client: acknowledges unsubscription.
     *
     * @param requestId the original unsubscribe request ID
     */
    record Unsubscribed(long requestId) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.UNSUBSCRIBED; }
    }

    /**
     * Router-to-client: delivers an event to a subscriber.
     *
     * @param subscriptionId the subscription identifier
     * @param publicationId  the publication identifier
     * @param details        event details (may include publisher session ID)
     * @param args           positional event arguments
     * @since 0.1.0
     */
    record Event(long subscriptionId, long publicationId, Map<String, Object> details, List<Object> args) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.EVENT; }
    }

    // --- Remote Procedure Call ---

    /**
     * Client-to-router: calls a remote procedure.
     *
     * @param requestId the request identifier
     * @param options   call options
     * @param procedure the procedure URI
     * @param args      positional arguments
     */
    record Call(long requestId, Map<String, Object> options, String procedure, List<Object> args) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.CALL; }
    }

    /**
     * Client-to-router: cancels a previously issued call.
     *
     * @param requestId the request ID of the original CALL
     * @param options   cancel options (e.g. "mode": "skip"|"kill"|"killnowait")
     * @since 0.1.0
     */
    record Cancel(long requestId, Map<String, Object> options) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.CANCEL; }
    }

    /**
     * Router-to-client: returns the result of a call.
     *
     * @param requestId the original call request ID
     * @param details   result details
     * @param args      positional result arguments
     */
    record Result(long requestId, Map<String, Object> details, List<Object> args) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.RESULT; }
    }

    /**
     * Client-to-router: registers a procedure.
     *
     * @param requestId the request identifier
     * @param options   registration options
     * @param procedure the procedure URI
     */
    record Register(long requestId, Map<String, Object> options, String procedure) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.REGISTER; }
    }

    /**
     * Router-to-client: acknowledges a procedure registration.
     *
     * @param requestId      the original register request ID
     * @param registrationId the assigned registration identifier
     */
    record Registered(long requestId, long registrationId) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.REGISTERED; }
    }

    /**
     * Client-to-router: unregisters a procedure.
     *
     * @param requestId      the request identifier
     * @param registrationId the registration to cancel
     */
    record Unregister(long requestId, long registrationId) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.UNREGISTER; }
    }

    /**
     * Router-to-client: acknowledges procedure unregistration.
     *
     * @param requestId the original unregister request ID
     */
    record Unregistered(long requestId) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.UNREGISTERED; }
    }

    /**
     * Router-to-callee: invokes a registered procedure.
     *
     * @param requestId      the invocation request ID
     * @param registrationId the registration being invoked
     * @param details        invocation details
     * @param args           positional arguments
     */
    record Invocation(long requestId, long registrationId, Map<String, Object> details, List<Object> args) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.INVOCATION; }
    }

    /**
     * Router-to-callee: interrupts a pending invocation.
     *
     * @param requestId the invocation request ID to interrupt
     * @param options   interrupt options (e.g. "mode": "kill"|"killnowait")
     * @since 0.1.0
     */
    record Interrupt(long requestId, Map<String, Object> options) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.INTERRUPT; }
    }

    /**
     * Callee-to-router: yields the result of an invocation.
     *
     * @param requestId the original invocation request ID
     * @param options   yield options
     * @param args      positional result arguments
     */
    record Yield(long requestId, Map<String, Object> options, List<Object> args) implements WampMessage {
        @Override public WampMessageType type() { return WampMessageType.YIELD; }
    }
}
