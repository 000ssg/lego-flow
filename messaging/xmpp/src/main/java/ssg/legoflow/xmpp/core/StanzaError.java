package ssg.legoflow.xmpp.core;

import java.util.Objects;

/**
 * XMPP stanza error as defined in RFC 6120.
 *
 * @param type      the error type category
 * @param condition the specific error condition
 * @param text      optional human-readable error text
 * @since 0.1.0
 */
public record StanzaError(ErrorType type, ErrorCondition condition, String text) {

    /**
     * Error type categories as defined in RFC 6120.
     *
     * @since 0.1.0
     */
    public enum ErrorType {
        /** Retry after providing credentials. */
        AUTH,
        /** Do not retry (the error cannot be remedied). */
        CANCEL,
        /** Proceed (the condition was only a warning). */
        CONTINUE,
        /** Retry after changing the data sent. */
        MODIFY,
        /** Retry after waiting (the error is temporary). */
        WAIT
    }

    /**
     * Defined error conditions as specified in RFC 6120.
     *
     * @since 0.1.0
     */
    public enum ErrorCondition {
        /** The sender has sent a stanza containing XML that does not conform to the schema. */
        BAD_REQUEST,
        /** Access cannot be granted because an existing resource or session already exists. */
        CONFLICT,
        /** The feature requested is not implemented. */
        FEATURE_NOT_IMPLEMENTED,
        /** The requesting entity does not possess the necessary permissions. */
        FORBIDDEN,
        /** The recipient or server can no longer be contacted at this address. */
        GONE,
        /** The server has experienced a misconfiguration or internal error. */
        INTERNAL_SERVER_ERROR,
        /** The addressed JID or item requested was not found. */
        ITEM_NOT_FOUND,
        /** The sending entity has provided a JID that is malformed. */
        JID_MALFORMED,
        /** The recipient or server understands the request but refuses to process it. */
        NOT_ACCEPTABLE,
        /** The recipient or server does not allow this type of stanza. */
        NOT_ALLOWED,
        /** The sender needs to provide proper credentials. */
        NOT_AUTHORIZED,
        /** The entity has violated some local service policy. */
        POLICY_VIOLATION,
        /** The intended recipient is temporarily unavailable. */
        RECIPIENT_UNAVAILABLE,
        /** The recipient or server is redirecting to another entity. */
        REDIRECT,
        /** The requesting entity is not authorized because registration is required. */
        REGISTRATION_REQUIRED,
        /** A remote server or service specified as part of the protocol cannot be found. */
        REMOTE_SERVER_NOT_FOUND,
        /** A remote server or service specified has timed out. */
        REMOTE_SERVER_TIMEOUT,
        /** The server or recipient is busy or lacks system resources. */
        RESOURCE_CONSTRAINT,
        /** The server or recipient does not currently provide the requested service. */
        SERVICE_UNAVAILABLE,
        /** The requesting entity is not authorized because a subscription is required. */
        SUBSCRIPTION_REQUIRED,
        /** The error condition is not one of those defined in this specification. */
        UNDEFINED_CONDITION,
        /** The recipient or server understood the request but was not expecting it. */
        UNEXPECTED_REQUEST
    }

    /**
     * Constructs a validated stanza error.
     */
    public StanzaError {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
    }

    /**
     * Serializes this error to XML.
     *
     * @return the XML string representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<error type=\"").append(type.name().toLowerCase()).append("\">");
        sb.append("<").append(conditionToElement(condition));
        sb.append(" xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>");
        if (text != null) {
            sb.append("<text xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\">");
            sb.append(text);
            sb.append("</text>");
        }
        sb.append("</error>");
        return sb.toString();
    }

    private static String conditionToElement(ErrorCondition condition) {
        return condition.name().toLowerCase().replace('_', '-');
    }
}
