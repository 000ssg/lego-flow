package ssg.legoflow.upnp.mediaserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UPnP ContentDirectory:1 service implementation.
 *
 * <p>Provides browse and search operations over a hierarchical content library.
 * The content directory maintains a tree of {@link ContentContainer} and
 * {@link ContentItem} objects that can be navigated by control points.
 *
 * @since 0.1.0
 */
public class ContentDirectory {

    /** UPnP service type for ContentDirectory:1. */
    public static final String SERVICE_TYPE = "urn:schemas-upnp-org:service:ContentDirectory:1";

    /** UPnP service ID for ContentDirectory. */
    public static final String SERVICE_ID = "urn:upnp-org:serviceId:ContentDirectory";

    /**
     * Browse flag indicating which items to return.
     *
     * @since 0.1.0
     */
    public enum BrowseFlag {

        /** Return direct children of the specified container. */
        BROWSE_DIRECT_CHILDREN("BrowseDirectChildren"),

        /** Return metadata for the specified object itself. */
        BROWSE_METADATA("BrowseMetadata");

        private final String value;

        BrowseFlag(String value) {
            this.value = value;
        }

        /**
         * Returns the UPnP string value.
         *
         * @return the value string
         * @since 0.1.0
         */
        public String value() {
            return value;
        }

        /**
         * Parses a browse flag from its string value.
         *
         * @param value the string value
         * @return the browse flag
         * @throws IllegalArgumentException if unknown
         * @since 0.1.0
         */
        public static BrowseFlag fromValue(String value) {
            for (BrowseFlag flag : values()) {
                if (flag.value.equals(value)) {
                    return flag;
                }
            }
            throw new IllegalArgumentException("Unknown browse flag: " + value);
        }
    }

    private final Map<String, ContentContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, ContentItem> allItems = new ConcurrentHashMap<>();
    private final AtomicLong systemUpdateId = new AtomicLong(0);
    private final Map<String, Long> containerUpdateIds = new ConcurrentHashMap<>();
    private final DidlLiteParser didlParser = new DidlLiteParser();
    private volatile ContentContainer rootContainer;

    /**
     * Creates a new content directory with an empty root container.
     *
     * @since 0.1.0
     */
    public ContentDirectory() {
        this.rootContainer = new ContentContainer("0", "-1", "Root", true);
        containers.put("0", rootContainer);
    }

    /**
     * Browses the content directory.
     *
     * @param objectId     the object ID to browse
     * @param flag         browse direct children or metadata
     * @param filter       comma-separated property filter ("*" for all)
     * @param startIndex   the starting index for pagination
     * @param requestCount the maximum number of items to return (0 for all)
     * @param sortCriteria the sort criteria string
     * @return the browse result
     * @since 0.1.0
     */
    public BrowseResult browse(String objectId, BrowseFlag flag, String filter,
                               int startIndex, int requestCount, String sortCriteria) {
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(flag, "flag must not be null");

        long updateId = systemUpdateId.get();

        if (flag == BrowseFlag.BROWSE_METADATA) {
            return browseMetadata(objectId, updateId);
        } else {
            return browseDirectChildren(objectId, startIndex, requestCount, updateId);
        }
    }

    /**
     * Searches the content directory for items matching the given UPnP search criteria.
     *
     * <p>Implements the UPnP ContentDirectory:1 Search action. The search criteria
     * string follows the UPnP search query language, supporting operators like
     * {@code contains}, {@code =}, {@code !=}, {@code derivedfrom}, {@code exists},
     * {@code doesNotContain}, and boolean combinators {@code and}/{@code or}.
     *
     * <p>When {@code containerId} is "0", searches the entire library. Otherwise,
     * searches only within the specified container's children.
     *
     * @param containerId    the container to search within ("0" for all)
     * @param searchCriteria the search criteria string (UPnP search query language)
     * @param filter         comma-separated property filter ("*" for all)
     * @param startIndex     the starting index for pagination
     * @param requestCount   the maximum number of items to return (0 for all)
     * @param sortCriteria   the sort criteria string
     * @return the browse result containing matching items
     * @since 0.1.0
     */
    public BrowseResult search(String containerId, String searchCriteria, String filter,
                               int startIndex, int requestCount, String sortCriteria) {
        long updateId = systemUpdateId.get();

        var predicate = SearchCriteria.parse(searchCriteria != null ? searchCriteria : "*");

        // Determine the search scope
        List<ContentItem> searchScope;
        if ("0".equals(containerId) || containerId == null || containerId.isEmpty()) {
            searchScope = new ArrayList<>(allItems.values());
        } else {
            ContentContainer container = containers.get(containerId);
            if (container != null) {
                searchScope = new ArrayList<>(container.getChildren());
            } else {
                searchScope = List.of();
            }
        }

        List<ContentItem> matches = searchScope.stream()
                .filter(predicate)
                .toList();

        int totalMatches = matches.size();
        int end = requestCount == 0 ? totalMatches : Math.min(startIndex + requestCount, totalMatches);
        List<ContentItem> page = startIndex < totalMatches
                ? matches.subList(startIndex, end) : List.of();

        String didlXml = didlParser.serialize(page);
        return new BrowseResult(didlXml, page.size(), totalMatches, updateId);
    }

    /**
     * Returns the search capabilities of this content directory.
     *
     * @return comma-separated list of searchable properties
     * @since 0.1.0
     */
    public String getSearchCapabilities() {
        return "dc:title,dc:creator,upnp:class,upnp:genre";
    }

    /**
     * Returns the sort capabilities of this content directory.
     *
     * @return comma-separated list of sortable properties
     * @since 0.1.0
     */
    public String getSortCapabilities() {
        return "dc:title,dc:creator,dc:date";
    }

    /**
     * Returns the system update ID, which increments on every content change.
     *
     * @return the current system update ID
     * @since 0.1.0
     */
    public long getSystemUpdateId() {
        return systemUpdateId.get();
    }

    /**
     * Returns the container update IDs as a comma-separated string of
     * {@code containerId,updateCount} pairs.
     *
     * <p>Each container that has been modified has an associated update count.
     * This allows control points to determine which containers have changed
     * since the last check.
     *
     * @return the container update IDs string (e.g. "0,3,1,1,2,2")
     * @since 0.1.0
     */
    public String getContainerUpdateIds() {
        if (containerUpdateIds.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        var entries = containerUpdateIds.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .toList();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(entries.get(i).getKey());
            sb.append(",");
            sb.append(entries.get(i).getValue());
        }
        return sb.toString();
    }

    /**
     * Returns the update count for a specific container.
     *
     * @param containerId the container ID
     * @return the update count, or 0 if the container has never been modified
     * @since 0.1.0
     */
    public long getContainerUpdateCount(String containerId) {
        return containerUpdateIds.getOrDefault(containerId, 0L);
    }

    /**
     * Adds a content item to the directory.
     *
     * @param item the item to add
     * @since 0.1.0
     */
    public void addContent(ContentItem item) {
        Objects.requireNonNull(item, "item must not be null");
        allItems.put(item.getId(), item);

        // Add to parent container and track container update
        ContentContainer parent = containers.get(item.getParentId());
        if (parent != null) {
            parent.addChild(item);
            containerUpdateIds.merge(item.getParentId(), 1L, Long::sum);
        }

        systemUpdateId.incrementAndGet();
    }

    /**
     * Removes a content item from the directory.
     *
     * @param itemId the ID of the item to remove
     * @return true if the item was found and removed
     * @since 0.1.0
     */
    public boolean removeContent(String itemId) {
        ContentItem removed = allItems.remove(itemId);
        if (removed != null) {
            ContentContainer parent = containers.get(removed.getParentId());
            if (parent != null) {
                parent.removeChild(itemId);
                containerUpdateIds.merge(removed.getParentId(), 1L, Long::sum);
            }
            systemUpdateId.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Adds a container to the directory.
     *
     * @param container the container to add
     * @since 0.1.0
     */
    public void addContainer(ContentContainer container) {
        Objects.requireNonNull(container, "container must not be null");
        containers.put(container.getId(), container);
        allItems.put(container.getId(), container.toContentItem());

        // Add container as child of its parent and track update
        ContentContainer parent = containers.get(container.getParentId());
        if (parent != null) {
            parent.addChild(container.toContentItem());
            containerUpdateIds.merge(container.getParentId(), 1L, Long::sum);
        }

        systemUpdateId.incrementAndGet();
    }

    /**
     * Sets the root container of the library, replacing the existing tree.
     *
     * @param root the root container
     * @since 0.1.0
     */
    public void setLibrary(ContentContainer root) {
        Objects.requireNonNull(root, "root must not be null");
        containers.clear();
        allItems.clear();
        this.rootContainer = root;
        indexContainer(root);
        systemUpdateId.incrementAndGet();
    }

    /**
     * Returns the root container.
     *
     * @return the root container
     * @since 0.1.0
     */
    public ContentContainer getRootContainer() {
        return rootContainer;
    }

    /**
     * Returns a container by ID.
     *
     * @param containerId the container ID
     * @return the container, or null if not found
     * @since 0.1.0
     */
    public ContentContainer getContainer(String containerId) {
        return containers.get(containerId);
    }

    /**
     * Returns a content item by ID.
     *
     * @param itemId the item ID
     * @return the item, or null if not found
     * @since 0.1.0
     */
    public ContentItem getItem(String itemId) {
        return allItems.get(itemId);
    }

    /**
     * Generates the SCPD (Service Control Protocol Description) XML for this service.
     *
     * @return the SCPD XML string
     * @since 0.1.0
     */
    public String generateScpd() {
        return """
                <?xml version="1.0"?>
                <scpd xmlns="urn:schemas-upnp-org:service-1-0">
                  <specVersion><major>1</major><minor>0</minor></specVersion>
                  <actionList>
                    <action>
                      <name>Browse</name>
                      <argumentList>
                        <argument><name>ObjectID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ObjectID</relatedStateVariable></argument>
                        <argument><name>BrowseFlag</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_BrowseFlag</relatedStateVariable></argument>
                        <argument><name>Filter</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Filter</relatedStateVariable></argument>
                        <argument><name>StartingIndex</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Index</relatedStateVariable></argument>
                        <argument><name>RequestedCount</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument>
                        <argument><name>SortCriteria</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SortCriteria</relatedStateVariable></argument>
                        <argument><name>Result</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Result</relatedStateVariable></argument>
                        <argument><name>NumberReturned</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument>
                        <argument><name>TotalMatches</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument>
                        <argument><name>UpdateID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_UpdateID</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>Search</name>
                      <argumentList>
                        <argument><name>ContainerID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ObjectID</relatedStateVariable></argument>
                        <argument><name>SearchCriteria</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SearchCriteria</relatedStateVariable></argument>
                        <argument><name>Filter</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Filter</relatedStateVariable></argument>
                        <argument><name>StartingIndex</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Index</relatedStateVariable></argument>
                        <argument><name>RequestedCount</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument>
                        <argument><name>SortCriteria</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SortCriteria</relatedStateVariable></argument>
                        <argument><name>Result</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Result</relatedStateVariable></argument>
                        <argument><name>NumberReturned</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument>
                        <argument><name>TotalMatches</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument>
                        <argument><name>UpdateID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_UpdateID</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetSearchCapabilities</name>
                      <argumentList>
                        <argument><name>SearchCaps</name><direction>out</direction><relatedStateVariable>SearchCapabilities</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetSortCapabilities</name>
                      <argumentList>
                        <argument><name>SortCaps</name><direction>out</direction><relatedStateVariable>SortCapabilities</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetSystemUpdateID</name>
                      <argumentList>
                        <argument><name>Id</name><direction>out</direction><relatedStateVariable>SystemUpdateID</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                  </actionList>
                  <serviceStateTable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_ObjectID</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Result</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_BrowseFlag</name><dataType>string</dataType>
                      <allowedValueList><allowedValue>BrowseMetadata</allowedValue><allowedValue>BrowseDirectChildren</allowedValue></allowedValueList>
                    </stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Filter</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_SortCriteria</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_SearchCriteria</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Index</name><dataType>ui4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Count</name><dataType>ui4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_UpdateID</name><dataType>ui4</dataType></stateVariable>
                    <stateVariable sendEvents="yes"><name>SystemUpdateID</name><dataType>ui4</dataType></stateVariable>
                    <stateVariable sendEvents="yes"><name>ContainerUpdateIDs</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>SearchCapabilities</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>SortCapabilities</name><dataType>string</dataType></stateVariable>
                  </serviceStateTable>
                </scpd>
                """;
    }

    // --- Private helpers ---

    private BrowseResult browseMetadata(String objectId, long updateId) {
        ContentItem item = allItems.get(objectId);
        if (item == null) {
            return new BrowseResult(didlParser.serialize(List.of()), 0, 0, updateId);
        }
        List<ContentItem> result = List.of(item);
        String didlXml = didlParser.serialize(result);
        return new BrowseResult(didlXml, 1, 1, updateId);
    }

    private BrowseResult browseDirectChildren(String objectId, int startIndex,
                                               int requestCount, long updateId) {
        ContentContainer container = containers.get(objectId);
        if (container == null) {
            return new BrowseResult(didlParser.serialize(List.of()), 0, 0, updateId);
        }

        int totalMatches = container.getChildCount();
        List<ContentItem> page = container.getChildren(startIndex, requestCount);
        String didlXml = didlParser.serialize(page);
        return new BrowseResult(didlXml, page.size(), totalMatches, updateId);
    }

    private void indexContainer(ContentContainer container) {
        containers.put(container.getId(), container);
        allItems.put(container.getId(), container.toContentItem());
        for (ContentItem child : container.getChildren()) {
            allItems.put(child.getId(), child);
        }
    }
}
