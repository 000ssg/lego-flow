package ssg.legoflow.http.websocket;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import java.util.*;
/**
 * WebSocket extension negotiation per RFC 6455 §11.6.
 *
 * <p>Handles the {@code Sec-WebSocket-Extensions} header for negotiating
 * extensions such as {@code permessage-deflate}. Each extension can have
 * parameters (e.g., {@code server_no_context_takeover}).
 *
 * @since 0.1.0
 */
public class WebSocketExtension {

    /** Standard extension name for per-message compression. */
    public static final String PERMESSAGE_DEFLATE = "permessage-deflate";

    /**
     * Represents a single extension offer with parameters.
     *
     * @param name       the extension name
     * @param parameters the extension parameters (name-value pairs)
     * @since 0.1.0
     */
    public record ExtensionOffer(String name, Map<String, String> parameters) {
        public ExtensionOffer {
            Objects.requireNonNull(name, "name must not be null");
            parameters = parameters != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(parameters))
                    : Map.of();
        }

        /**
         * Returns true if the parameter is present (with or without a value).
         *
         * @param paramName the parameter name
         * @return true if present
         */
        public boolean hasParameter(String paramName) {
            return parameters.containsKey(paramName);
        }

        @Override
        public String toString() {
            var sb = new StringBuilder(name);
            for (var entry : parameters.entrySet()) {
                sb.append("; ").append(entry.getKey());
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    sb.append("=").append(entry.getValue());
                }
            }
            return sb.toString();
        }
    }

    /**
     * Parses the Sec-WebSocket-Extensions header from a request.
     *
     * @param request the HTTP request
     * @return list of extension offers
     */
    public List<ExtensionOffer> parseRequestedExtensions(HttpRequest request) {
        String header = request.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_EXTENSIONS);
        return parseExtensionHeader(header);
    }

    /**
     * Parses a Sec-WebSocket-Extensions header value.
     *
     * <p>Format: {@code ext1; param1=val1; param2, ext2; param3}
     *
     * @param headerValue the header value
     * @return list of parsed extension offers
     */
    public List<ExtensionOffer> parseExtensionHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return List.of();
        }
        List<ExtensionOffer> offers = new ArrayList<>();
        for (String extensionStr : headerValue.split(",")) {
            String[] parts = extensionStr.trim().split(";");
            String name = parts[0].trim();
            if (name.isEmpty()) continue;

            Map<String, String> params = new LinkedHashMap<>();
            for (int i = 1; i < parts.length; i++) {
                String param = parts[i].trim();
                int eq = param.indexOf('=');
                if (eq > 0) {
                    params.put(param.substring(0, eq).trim(),
                            param.substring(eq + 1).trim());
                } else {
                    params.put(param, "");
                }
            }
            offers.add(new ExtensionOffer(name, params));
        }
        return Collections.unmodifiableList(offers);
    }

    /**
     * Negotiates extensions from the client's offers against the server's supported extensions.
     *
     * @param clientOffers       the client's extension offers
     * @param supportedNames     the set of extension names the server supports
     * @return the list of accepted extensions
     */
    public List<ExtensionOffer> negotiate(List<ExtensionOffer> clientOffers,
                                           Set<String> supportedNames) {
        if (clientOffers == null || supportedNames == null) {
            return List.of();
        }
        List<ExtensionOffer> accepted = new ArrayList<>();
        for (ExtensionOffer offer : clientOffers) {
            if (supportedNames.contains(offer.name())) {
                accepted.add(offer);
            }
        }
        return Collections.unmodifiableList(accepted);
    }

    /**
     * Sets the negotiated extensions on the handshake response.
     *
     * @param response   the WebSocket handshake response
     * @param extensions the negotiated extensions
     */
    public void setNegotiatedExtensions(HttpResponse response, List<ExtensionOffer> extensions) {
        if (extensions != null && !extensions.isEmpty()) {
            var sb = new StringBuilder();
            for (int i = 0; i < extensions.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(extensions.get(i).toString());
            }
            response.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_EXTENSIONS, sb.toString());
        }
    }

    /**
     * Extracts the accepted extensions from the server's handshake response.
     *
     * @param response the WebSocket handshake response
     * @return the list of accepted extensions
     */
    public List<ExtensionOffer> getAcceptedExtensions(HttpResponse response) {
        String header = response.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_EXTENSIONS);
        return parseExtensionHeader(header);
    }

    /**
     * Creates a permessage-deflate extension offer with default parameters.
     *
     * @return the permessage-deflate extension offer
     */
    public static ExtensionOffer permessageDeflate() {
        return new ExtensionOffer(PERMESSAGE_DEFLATE, Map.of());
    }

    /**
     * Creates a permessage-deflate extension offer with server_no_context_takeover.
     *
     * @return the permessage-deflate extension offer with no context takeover
     */
    public static ExtensionOffer permessageDeflateNoContextTakeover() {
        return new ExtensionOffer(PERMESSAGE_DEFLATE,
                Map.of("server_no_context_takeover", "", "client_no_context_takeover", ""));
    }
}
