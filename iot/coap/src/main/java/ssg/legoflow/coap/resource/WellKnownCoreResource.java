package ssg.legoflow.coap.resource;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.ContentFormat;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
/**
 * The {@code /.well-known/core} resource for CoAP resource discovery (RFC 6690).
 *
 * <p>Returns a link-format listing of all registered resources, supporting
 * query filtering by resource type ({@code ?rt=}) and interface description ({@code ?if=}).
 *
 * @since 0.1.0
 */
public final class WellKnownCoreResource extends CoapResource {

    /** The well-known URI for CoRE resource discovery. */
    public static final String PATH = "/.well-known/core";

    private final Supplier<List<CoapResource>> resourceProvider;

    /**
     * Creates the well-known/core resource with a supplier for registered resources.
     *
     * @param resourceProvider a supplier returning all registered resources
     * @since 0.1.0
     */
    public WellKnownCoreResource(Supplier<List<CoapResource>> resourceProvider) {
        super("core", PATH, false);
        this.resourceProvider = Objects.requireNonNull(resourceProvider, "resourceProvider must not be null");
        getAttributes().resourceType("core.rd")
                .contentFormat(ContentFormat.APPLICATION_LINK_FORMAT.value());
    }

    /**
     * Handles GET requests by returning the link-format resource listing.
     *
     * <p>Supports query parameters:
     * <ul>
     *   <li>{@code rt} — filter by resource type</li>
     *   <li>{@code if} — filter by interface description</li>
     * </ul>
     *
     * @param exchange the exchange context
     * @since 0.1.0
     */
    @Override
    public void handleGet(CoapExchange exchange) {
        var resources = resourceProvider.get();
        var params = exchange.getQueryParameters();

        String rtFilter = params.get("rt");
        String ifFilter = params.get("if");

        var filtered = new ArrayList<CoapResource>();
        for (var resource : resources) {
            if (resource == this) continue; // Don't list ourselves

            var attrs = resource.getAttributes();
            if (rtFilter != null && !rtFilter.equals(attrs.resourceType())) {
                continue;
            }
            if (ifFilter != null && !ifFilter.equals(attrs.interfaceDescription())) {
                continue;
            }
            filtered.add(resource);
        }

        var sb = new StringBuilder();
        for (int i = 0; i < filtered.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(filtered.get(i).toCoreLinkFormat());
        }

        exchange.respond(CoapCode.CONTENT,
                sb.toString().getBytes(StandardCharsets.UTF_8),
                ContentFormat.APPLICATION_LINK_FORMAT.value());
    }
}
