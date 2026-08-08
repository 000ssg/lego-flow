package ssg.legoflow.upnp.mediaserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a container (folder) in a DLNA content directory.
 *
 * <p>Containers hold child items and sub-containers, forming a tree
 * structure that maps to the {@code <container>} elements in DIDL-Lite XML.
 *
 * @since 0.1.0
 */
public class ContentContainer {

    private final String id;
    private final String parentId;
    private final String title;
    private final boolean searchable;
    private final List<ContentItem> children;

    /**
     * Creates a new content container.
     *
     * @param id         the unique container ID
     * @param parentId   the parent container ID ("-1" for root)
     * @param title      the display title
     * @param searchable whether the container supports search
     * @since 0.1.0
     */
    public ContentContainer(String id, String parentId, String title, boolean searchable) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.parentId = Objects.requireNonNull(parentId, "parentId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.searchable = searchable;
        this.children = new CopyOnWriteArrayList<>();
    }

    /**
     * Returns the unique container ID.
     *
     * @return the container ID
     * @since 0.1.0
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the parent container ID.
     *
     * @return the parent ID ("-1" for root)
     * @since 0.1.0
     */
    public String getParentId() {
        return parentId;
    }

    /**
     * Returns the display title.
     *
     * @return the title
     * @since 0.1.0
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the number of direct children.
     *
     * @return the child count
     * @since 0.1.0
     */
    public int getChildCount() {
        return children.size();
    }

    /**
     * Returns whether this container supports search.
     *
     * @return true if searchable
     * @since 0.1.0
     */
    public boolean isSearchable() {
        return searchable;
    }

    /**
     * Returns an unmodifiable view of the children.
     *
     * @return the list of child items
     * @since 0.1.0
     */
    public List<ContentItem> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Adds a child item to this container.
     *
     * @param item the item to add
     * @return this container for chaining
     * @since 0.1.0
     */
    public ContentContainer addChild(ContentItem item) {
        Objects.requireNonNull(item, "item must not be null");
        children.add(item);
        return this;
    }

    /**
     * Removes a child item by ID.
     *
     * @param itemId the ID of the item to remove
     * @return true if an item was removed
     * @since 0.1.0
     */
    public boolean removeChild(String itemId) {
        return children.removeIf(item -> item.getId().equals(itemId));
    }

    /**
     * Returns a paginated sublist of children.
     *
     * @param startIndex   the starting index (0-based)
     * @param requestCount the maximum number of items to return (0 for all)
     * @return the sublist of children
     * @since 0.1.0
     */
    public List<ContentItem> getChildren(int startIndex, int requestCount) {
        int size = children.size();
        if (startIndex >= size) {
            return List.of();
        }
        int end = requestCount == 0 ? size : Math.min(startIndex + requestCount, size);
        return new ArrayList<>(children.subList(startIndex, end));
    }

    /**
     * Converts this container to a {@link ContentItem} representation for inclusion in browse results.
     *
     * @return a content item representing this container
     * @since 0.1.0
     */
    public ContentItem toContentItem() {
        return new ContentItem(id, parentId, title, ContentItemType.CONTAINER);
    }

    @Override
    public String toString() {
        return "ContentContainer{id='" + id + "', title='" + title + "', children=" + children.size() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentContainer that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
