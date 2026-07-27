package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lazy-loading {@link TreeModel} for browsing a media server content hierarchy.
 *
 * <p>The root node represents the server root container (object ID "0").
 * Children are loaded lazily on first access via {@link MediaServerProxy#browse(String)}
 * and cached for subsequent requests. The model supports refresh to reload content.
 *
 * @since 1.0.0
 */
public class ContentTreeModel implements TreeModel {

    private static final Logger LOG = LoggerFactory.getLogger(ContentTreeModel.class);

    private final MediaServerProxy server;
    private final ContentTreeNode root;
    private final Map<String, List<ContentTreeNode>> childCache = new ConcurrentHashMap<>();
    private final List<TreeModelListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new tree model for the given media server.
     *
     * @param server the media server proxy to browse
     * @since 1.0.0
     */
    public ContentTreeModel(MediaServerProxy server) {
        this.server = server;
        this.root = new ContentTreeNode("0", "Root", true);
    }

    /**
     * Returns the media server proxy used by this model.
     *
     * @return the server proxy
     * @since 1.0.0
     */
    public MediaServerProxy getServer() {
        return server;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0.0
     */
    @Override
    public Object getRoot() {
        return root;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0.0
     */
    @Override
    public Object getChild(Object parent, int index) {
        if (parent instanceof ContentTreeNode node) {
            var children = getChildren(node);
            if (index >= 0 && index < children.size()) {
                return children.get(index);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0.0
     */
    @Override
    public int getChildCount(Object parent) {
        if (parent instanceof ContentTreeNode node && node.isContainer()) {
            return getChildren(node).size();
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0.0
     */
    @Override
    public boolean isLeaf(Object node) {
        if (node instanceof ContentTreeNode treeNode) {
            return !treeNode.isContainer();
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0.0
     */
    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // Not editable
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0.0
     */
    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent instanceof ContentTreeNode node && child instanceof ContentTreeNode) {
            return getChildren(node).indexOf(child);
        }
        return -1;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0.0
     */
    @Override
    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0.0
     */
    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }

    /**
     * Returns the cached children of a node, loading them lazily if needed.
     *
     * @param node the parent node
     * @return the list of child nodes
     * @since 1.0.0
     */
    public List<ContentTreeNode> getChildren(ContentTreeNode node) {
        return childCache.computeIfAbsent(node.getId(), id -> loadChildren(id));
    }

    /**
     * Clears the cache and reloads the tree from the server.
     *
     * @since 1.0.0
     */
    public void refresh() {
        childCache.clear();
        fireTreeStructureChanged();
    }

    /**
     * Clears cached children for a specific node, forcing a reload on next access.
     *
     * @param nodeId the node ID to invalidate
     * @since 1.0.0
     */
    public void invalidate(String nodeId) {
        childCache.remove(nodeId);
    }

    /**
     * Loads children for a given container from the media server.
     *
     * @param containerId the container object ID
     * @return the list of child tree nodes
     * @since 1.0.0
     */
    List<ContentTreeNode> loadChildren(String containerId) {
        try {
            List<ContentItem> items = server.browse(containerId);
            return items.stream()
                    .map(item -> new ContentTreeNode(
                            item.getId(),
                            item.getTitle(),
                            item.getType() == ContentItemType.CONTAINER,
                            item))
                    .toList();
        } catch (Exception e) {
            LOG.warn("Failed to load children for container '{}' on server '{}': {}",
                    containerId, server.getFriendlyName(), e.getMessage());
            return List.of();
        }
    }

    private void fireTreeStructureChanged() {
        var event = new TreeModelEvent(this, new Object[]{root});
        for (TreeModelListener l : listeners) {
            l.treeStructureChanged(event);
        }
    }

    /**
     * Node in the content tree representing either a container or a leaf item.
     *
     * @since 1.0.0
     */
    public static final class ContentTreeNode {

        private final String id;
        private final String title;
        private final boolean container;
        private final ContentItem contentItem;

        /**
         * Creates a new tree node.
         *
         * @param id        the content object ID
         * @param title     the display title
         * @param container true if this node is a container
         * @since 1.0.0
         */
        public ContentTreeNode(String id, String title, boolean container) {
            this(id, title, container, null);
        }

        /**
         * Creates a new tree node with an associated content item.
         *
         * @param id          the content object ID
         * @param title       the display title
         * @param container   true if this node is a container
         * @param contentItem the full content item, may be {@code null}
         * @since 1.0.0
         */
        public ContentTreeNode(String id, String title, boolean container, ContentItem contentItem) {
            this.id = id;
            this.title = title;
            this.container = container;
            this.contentItem = contentItem;
        }

        /**
         * Returns the content object ID.
         *
         * @return the object ID
         * @since 1.0.0
         */
        public String getId() {
            return id;
        }

        /**
         * Returns the display title.
         *
         * @return the title
         * @since 1.0.0
         */
        public String getTitle() {
            return title;
        }

        /**
         * Returns whether this node represents a container.
         *
         * @return true if container
         * @since 1.0.0
         */
        public boolean isContainer() {
            return container;
        }

        /**
         * Returns the full content item associated with this node, if available.
         *
         * @return the content item, or {@code null} for the root node
         * @since 1.0.0
         */
        public ContentItem getContentItem() {
            return contentItem;
        }

        @Override
        public String toString() {
            return title;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ContentTreeNode that)) return false;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
