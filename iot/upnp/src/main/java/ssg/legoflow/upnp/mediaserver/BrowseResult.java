package ssg.legoflow.upnp.mediaserver;

/**
 * Result of a ContentDirectory browse or search operation.
 *
 * <p>Contains the DIDL-Lite XML response along with pagination metadata
 * as defined by the UPnP ContentDirectory:1 service specification.
 *
 * @param didlXml         the DIDL-Lite XML fragment containing the result items
 * @param numberReturned  the number of items returned in this response
 * @param totalMatches    the total number of items matching the query
 * @param updateId        the system update ID at the time of the response
 * @since 1.0.0
 */
public record BrowseResult(
        String didlXml,
        int numberReturned,
        int totalMatches,
        long updateId
) {
}
