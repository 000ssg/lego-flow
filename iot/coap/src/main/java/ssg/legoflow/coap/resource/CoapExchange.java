package ssg.legoflow.coap.resource;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a CoAP request/response exchange context for resource handlers.
 *
 * <p>Provides access to the incoming request and methods for constructing
 * and sending a response. Supports both piggybacked responses (carried in the ACK)
 * and separate responses (empty ACK now, actual response sent later as a CON message).
 *
 * @since 1.0.0
 */
public final class CoapExchange {

    private final CoapMessage request;
    private final SocketAddress source;
    private CoapMessage response;
    private boolean separateResponse = false;

    /**
     * Creates a new exchange for the given request.
     *
     * @param request the incoming request message
     * @param source  the source address of the request
     * @throws NullPointerException if {@code request} or {@code source} is {@code null}
     * @since 1.0.0
     */
    public CoapExchange(CoapMessage request, SocketAddress source) {
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    /**
     * Returns the incoming request message.
     *
     * @return the request
     * @since 1.0.0
     */
    public CoapMessage getRequest() {
        return request;
    }

    /**
     * Returns the source address of the request.
     *
     * @return the source socket address
     * @since 1.0.0
     */
    public SocketAddress getSource() {
        return source;
    }

    /**
     * Returns the request options.
     *
     * @return the request options list
     * @since 1.0.0
     */
    public java.util.List<CoapOption> getRequestOptions() {
        return request.options();
    }

    /**
     * Returns query parameters parsed from Uri-Query options.
     *
     * @return a map of query parameter keys to values
     * @since 1.0.0
     */
    public Map<String, String> getQueryParameters() {
        var queries = request.getOptions(CoapOption.URI_QUERY);
        if (queries.isEmpty()) {
            return Collections.emptyMap();
        }
        var params = new LinkedHashMap<String, String>();
        for (var query : queries) {
            var str = query.asString();
            int eq = str.indexOf('=');
            if (eq >= 0) {
                params.put(str.substring(0, eq), str.substring(eq + 1));
            } else {
                params.put(str, "");
            }
        }
        return Collections.unmodifiableMap(params);
    }

    /**
     * Responds with the given code, payload, and content format.
     *
     * @param code          the response code
     * @param payload       the response payload
     * @param contentFormat the content format identifier
     * @since 1.0.0
     */
    public void respond(CoapCode code, byte[] payload, int contentFormat) {
        Objects.requireNonNull(code, "code must not be null");
        var builder = CoapMessage.builder()
                .type(request.type() == CoapType.CONFIRMABLE ? CoapType.ACKNOWLEDGEMENT : CoapType.NON_CONFIRMABLE)
                .code(code)
                .messageId(request.messageId())
                .token(request.token());

        if (contentFormat >= 0) {
            builder.option(CoapOption.contentFormat(contentFormat));
        }
        if (payload != null) {
            builder.payload(payload);
        }
        this.response = builder.build();
    }

    /**
     * Responds with the given code only (no payload).
     *
     * @param code the response code
     * @since 1.0.0
     */
    public void respond(CoapCode code) {
        respond(code, null, -1);
    }

    /**
     * Responds with 2.05 Content and the given payload.
     *
     * @param payload the response payload
     * @since 1.0.0
     */
    public void respond(byte[] payload) {
        respond(CoapCode.CONTENT, payload, -1);
    }

    /**
     * Responds with 2.05 Content, the given payload, and content format.
     *
     * @param payload       the response payload
     * @param contentFormat the content format identifier
     * @since 1.0.0
     */
    public void respond(byte[] payload, int contentFormat) {
        respond(CoapCode.CONTENT, payload, contentFormat);
    }

    /**
     * Returns the response message, or {@code null} if no response has been set.
     *
     * @return the response message, or {@code null}
     * @since 1.0.0
     */
    public CoapMessage getResponse() {
        return response;
    }

    /**
     * Returns whether a response has been set.
     *
     * @return {@code true} if a response exists
     * @since 1.0.0
     */
    public boolean hasResponse() {
        return response != null;
    }

    /**
     * Marks this exchange as requiring a separate response (RFC 7252 Section 5.2.2).
     *
     * <p>When marked, the server will immediately send an empty ACK to acknowledge
     * the CON request, and the actual response will be sent later as a new CON
     * message with the same token. This is used when the server cannot produce
     * the response quickly enough for a piggybacked response.
     *
     * @since 1.0.0
     */
    public void markSeparateResponse() {
        this.separateResponse = true;
    }

    /**
     * Returns whether this exchange requires a separate response.
     *
     * @return {@code true} if a separate response is needed
     * @since 1.0.0
     */
    public boolean isSeparateResponse() {
        return separateResponse;
    }

    /**
     * Responds with a separate (delayed) response as a new CON message.
     *
     * <p>Unlike piggybacked responses which reuse the request's message ID,
     * a separate response is sent as a new CON message with a new message ID
     * but the same token as the original request.
     *
     * @param code          the response code
     * @param payload       the response payload
     * @param contentFormat the content format identifier
     * @param newMessageId  the new message ID for the separate response
     * @since 1.0.0
     */
    public void respondSeparate(CoapCode code, byte[] payload, int contentFormat, int newMessageId) {
        Objects.requireNonNull(code, "code must not be null");
        var builder = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(code)
                .messageId(newMessageId)
                .token(request.token());

        if (contentFormat >= 0) {
            builder.option(CoapOption.contentFormat(contentFormat));
        }
        if (payload != null) {
            builder.payload(payload);
        }
        this.response = builder.build();
    }
}
