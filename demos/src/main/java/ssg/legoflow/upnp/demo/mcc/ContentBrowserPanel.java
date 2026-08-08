package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Media library browser panel with tree navigation, content table, breadcrumbs, and search.
 *
 * <p>The left side shows a {@link JTree} for folder hierarchy navigation using a
 * lazy-loading {@link ContentTreeModel}. The right side shows a {@link JTable}
 * listing the contents of the currently selected container with columns for type,
 * title, creator, duration, size, and format. A breadcrumb bar at the top shows
 * the current path and allows quick navigation. A search bar supports full-text
 * search via {@link MediaServerProxy#search(String)}.
 *
 * <p>This panel uses {@link DarkTheme} colours to match the web variant's CSS
 * dark theme, including alternating row colours, styled tree/table components,
 * dark breadcrumb buttons, and emoji-based type icons.
 *
 * @since 0.1.0
 */
public class ContentBrowserPanel extends JPanel {

    /** Alternating row colour (slightly lighter than body background). */
    private static final Color ALT_ROW = new Color(0x162032);

    private final JPanel breadcrumbPanel;
    private final JTree contentTree;
    private final JTable contentTable;
    private final ContentTableModel tableModel;
    private final JTextField searchField;

    private MediaServerProxy currentServer;
    private ContentTreeModel treeModel;
    private final List<BreadcrumbEntry> breadcrumbs = new ArrayList<>();
    private Consumer<ContentItem> playOnRendererAction;
    private Consumer<ContentItem> playLocallyAction;

    /**
     * Creates a new content browser panel styled with the dark theme.
     *
     * @since 0.1.0
     */
    public ContentBrowserPanel() {
        setLayout(new BorderLayout(4, 4));
        setBackground(DarkTheme.PANEL_BG);
        setBorder(DarkTheme.panelBorder("Content Browser"));

        // ---- Top bar: breadcrumbs + search ----
        var topPanel = new JPanel(new BorderLayout(4, 0));
        topPanel.setBackground(DarkTheme.PANEL_BG);

        breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        breadcrumbPanel.setBackground(DarkTheme.PANEL_BG);
        var breadcrumbScroll = new JScrollPane(breadcrumbPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        breadcrumbScroll.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        breadcrumbScroll.getViewport().setBackground(DarkTheme.PANEL_BG);
        topPanel.add(breadcrumbScroll, BorderLayout.CENTER);

        var searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.setBackground(DarkTheme.PANEL_BG);
        searchField = new JTextField(16);
        searchField.setBackground(DarkTheme.BODY_BG);
        searchField.setForeground(DarkTheme.TEXT);
        searchField.setCaretColor(DarkTheme.TEXT);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        var searchButton = new JButton("Search");
        styleButton(searchButton);
        searchButton.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        topPanel.add(searchPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // ---- Tree (left) ----
        contentTree = new JTree();
        contentTree.setRootVisible(true);
        DarkTheme.styleTree(contentTree);
        contentTree.addTreeSelectionListener(e -> {
            var path = contentTree.getSelectionPath();
            if (path != null) {
                var node = path.getLastPathComponent();
                if (node instanceof ContentTreeModel.ContentTreeNode treeNode && treeNode.isContainer()) {
                    navigateTo(treeNode.getId(), treeNode.getTitle(), false);
                }
            }
        });

        // DnD: enable drag for non-container tree items
        contentTree.setDragEnabled(true);
        contentTree.setTransferHandler(new TreeContentTransferHandler());

        // Handle clicks on the root node even when already selected (JTree doesn't
        // fire selection-change events when clicking an already-selected node)
        contentTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                var path = contentTree.getPathForLocation(e.getX(), e.getY());
                if (path != null && path.getPathCount() == 1) {
                    var node = path.getLastPathComponent();
                    if (node instanceof ContentTreeModel.ContentTreeNode treeNode) {
                        navigateTo(treeNode.getId(), treeNode.getTitle(), false);
                    }
                }
            }
        });

        // ---- Table (right) ----
        tableModel = new ContentTableModel();
        contentTable = new JTable(tableModel);
        contentTable.setFillsViewportHeight(true);
        contentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contentTable.setRowHeight(24);
        DarkTheme.styleTable(contentTable);

        contentTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        contentTable.getColumnModel().getColumn(0).setMaxWidth(40);
        contentTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        contentTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        contentTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        contentTable.getColumnModel().getColumn(3).setCellRenderer(new DurationCellRenderer());
        contentTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        contentTable.getColumnModel().getColumn(4).setCellRenderer(new SizeCellRenderer());
        contentTable.getColumnModel().getColumn(5).setPreferredWidth(100);

        // DnD: enable drag for content items
        contentTable.setDragEnabled(true);
        contentTable.setDropMode(DropMode.ON);
        contentTable.setTransferHandler(new ContentTransferHandler());

        // Set grab cursor for non-container items via mouse motion
        contentTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = contentTable.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    ContentItem item = tableModel.getItemAt(row);
                    if (item.getType() != ContentItemType.CONTAINER) {
                        contentTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    } else {
                        contentTable.setCursor(Cursor.getDefaultCursor());
                    }
                } else {
                    contentTable.setCursor(Cursor.getDefaultCursor());
                }
            }
        });

        contentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showTableContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showTableContextMenu(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                contentTable.setCursor(Cursor.getDefaultCursor());
            }
        });

        // ---- Split pane ----
        var treeScroll = new JScrollPane(contentTree);
        treeScroll.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        treeScroll.getViewport().setBackground(DarkTheme.BODY_BG);

        var tableScroll = new JScrollPane(contentTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        tableScroll.getViewport().setBackground(DarkTheme.BODY_BG);

        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, tableScroll);
        splitPane.setDividerLocation(180);
        splitPane.setBackground(DarkTheme.PANEL_BG);
        splitPane.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        splitPane.setDividerSize(6);
        // Style the divider via UI defaults (already set by DarkTheme.apply())
        splitPane.setUI(splitPane.getUI()); // force refresh of divider colours
        add(splitPane, BorderLayout.CENTER);

        updateBreadcrumbs();
    }

    /**
     * Sets the action invoked when "Play on Renderer" is selected.
     *
     * @param action the consumer receiving the content item to play
     * @since 0.1.0
     */
    public void setPlayOnRendererAction(Consumer<ContentItem> action) {
        this.playOnRendererAction = action;
    }

    /**
     * Sets the action invoked when "Play Locally" is selected.
     *
     * @param action the consumer receiving the content item to play locally
     * @since 0.1.0
     */
    public void setPlayLocallyAction(Consumer<ContentItem> action) {
        this.playLocallyAction = action;
    }

    /**
     * Sets the media server to browse and loads its root content.
     *
     * @param server the media server proxy
     * @since 0.1.0
     */
    public void setServer(MediaServerProxy server) {
        this.currentServer = server;
        if (server != null) {
            treeModel = new ContentTreeModel(server);
            contentTree.setModel(treeModel);
            breadcrumbs.clear();
            breadcrumbs.add(new BreadcrumbEntry("0", "Root"));
            updateBreadcrumbs();
            loadContent("0");
        } else {
            treeModel = null;
            contentTree.setModel(null);
            tableModel.setItems(List.of());
            breadcrumbs.clear();
            updateBreadcrumbs();
        }
    }

    /**
     * Navigates into a container, updating the breadcrumb trail and content table.
     *
     * @param containerId    the container object ID
     * @param title          the container title for the breadcrumb
     * @param fromBreadcrumb whether this navigation is from a breadcrumb click
     * @since 0.1.0
     */
    public void navigateTo(String containerId, String title, boolean fromBreadcrumb) {
        if (fromBreadcrumb) {
            // Remove breadcrumbs after the clicked one
            int index = -1;
            for (int i = 0; i < breadcrumbs.size(); i++) {
                if (breadcrumbs.get(i).id().equals(containerId)) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                breadcrumbs.subList(index + 1, breadcrumbs.size()).clear();
            }
        } else {
            // Check if we're already in this path
            boolean found = false;
            for (int i = 0; i < breadcrumbs.size(); i++) {
                if (breadcrumbs.get(i).id().equals(containerId)) {
                    breadcrumbs.subList(i + 1, breadcrumbs.size()).clear();
                    found = true;
                    break;
                }
            }
            if (!found) {
                breadcrumbs.add(new BreadcrumbEntry(containerId, title));
            }
        }
        updateBreadcrumbs();
        loadContent(containerId);
    }

    /**
     * Refreshes the current content view.
     *
     * @since 0.1.0
     */
    public void refresh() {
        if (treeModel != null) {
            treeModel.refresh();
        }
        if (!breadcrumbs.isEmpty()) {
            loadContent(breadcrumbs.getLast().id());
        }
    }

    private void loadContent(String containerId) {
        if (currentServer == null) return;

        var worker = new SwingWorker<List<ContentItem>, Void>() {
            @Override
            protected List<ContentItem> doInBackground() {
                return currentServer.browse(containerId);
            }

            @Override
            protected void done() {
                try {
                    tableModel.setItems(get());
                } catch (Exception e) {
                    tableModel.setItems(List.of());
                    String message = extractErrorMessage(e);
                    showDiagnosticMessage("Browse failed for container '" + containerId
                            + "' on server '" + currentServer.getFriendlyName() + "': " + message);
                }
            }
        };
        worker.execute();
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty() || currentServer == null) return;

        var worker = new SwingWorker<List<ContentItem>, Void>() {
            @Override
            protected List<ContentItem> doInBackground() {
                return currentServer.search(query);
            }

            @Override
            protected void done() {
                try {
                    tableModel.setItems(get());
                    breadcrumbs.clear();
                    breadcrumbs.add(new BreadcrumbEntry("search", "Search: " + query));
                    updateBreadcrumbs();
                } catch (Exception e) {
                    tableModel.setItems(List.of());
                    String message = extractErrorMessage(e);
                    showDiagnosticMessage("Search failed for '" + query + "' on server '"
                            + currentServer.getFriendlyName() + "': " + message);
                }
            }
        };
        worker.execute();
    }

    private void handleDoubleClick() {
        int row = contentTable.getSelectedRow();
        if (row < 0) return;
        ContentItem item = tableModel.getItemAt(row);
        if (item.getType() == ContentItemType.CONTAINER) {
            navigateTo(item.getId(), item.getTitle(), false);
        } else if (playOnRendererAction != null) {
            playOnRendererAction.accept(item);
        }
    }

    private void showTableContextMenu(MouseEvent e) {
        int row = contentTable.rowAtPoint(e.getPoint());
        if (row < 0) return;
        contentTable.setRowSelectionInterval(row, row);
        ContentItem item = tableModel.getItemAt(row);

        var popup = new JPopupMenu();
        popup.setBackground(DarkTheme.PANEL_BG);
        popup.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));

        if (item.getType() != ContentItemType.CONTAINER) {
            var playRenderer = new JMenuItem("Play on Renderer");
            styleMenuItem(playRenderer);
            playRenderer.addActionListener(ev -> {
                if (playOnRendererAction != null) playOnRendererAction.accept(item);
            });
            popup.add(playRenderer);

            var playLocal = new JMenuItem("Play Locally");
            styleMenuItem(playLocal);
            playLocal.addActionListener(ev -> {
                if (playLocallyAction != null) playLocallyAction.accept(item);
            });
            popup.add(playLocal);
            popup.addSeparator();
        }

        var properties = new JMenuItem("Properties");
        styleMenuItem(properties);
        properties.addActionListener(ev -> showItemProperties(item));
        popup.add(properties);

        popup.show(contentTable, e.getX(), e.getY());
    }

    private void showItemProperties(ContentItem item) {
        var sb = new StringBuilder();
        sb.append("Title: ").append(item.getTitle()).append("\n");
        sb.append("ID: ").append(item.getId()).append("\n");
        sb.append("Type: ").append(item.getType()).append("\n");
        if (item.getCreator() != null) sb.append("Creator: ").append(item.getCreator()).append("\n");
        if (item.getDuration() != null) sb.append("Duration: ")
                .append(ContentItem.formatDuration(item.getDuration())).append("\n");
        if (item.getSize() > 0) sb.append("Size: ").append(formatSize(item.getSize())).append("\n");
        if (item.getResourceUrl() != null) sb.append("URL: ").append(item.getResourceUrl()).append("\n");
        if (item.getProtocolInfo() != null) sb.append("Protocol: ").append(item.getProtocolInfo()).append("\n");

        JOptionPane.showMessageDialog(this, sb.toString(), "Item Properties", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateBreadcrumbs() {
        breadcrumbPanel.removeAll();
        for (int i = 0; i < breadcrumbs.size(); i++) {
            if (i > 0) {
                var separator = new JLabel(" › ");
                separator.setForeground(DarkTheme.MUTED_TEXT);
                breadcrumbPanel.add(separator);
            }
            var entry = breadcrumbs.get(i);
            var button = new JButton(entry.title());
            button.setBackground(DarkTheme.PANEL_BG);
            button.setForeground(DarkTheme.TEXT);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(DarkTheme.BORDER),
                    BorderFactory.createEmptyBorder(1, 6, 1, 6)));
            button.setMargin(new Insets(1, 4, 1, 4));
            button.setFocusPainted(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.putClientProperty("breadcrumbId", entry.id());
            button.addActionListener(e -> navigateTo(entry.id(), entry.title(), true));
            breadcrumbPanel.add(button);
        }
        breadcrumbPanel.revalidate();
        breadcrumbPanel.repaint();
    }

    /**
     * Applies dark button styling to a standard {@link JButton}.
     *
     * @param button the button to style
     * @since 0.1.0
     */
    private static void styleButton(JButton button) {
        button.setBackground(DarkTheme.PANEL_BG);
        button.setForeground(DarkTheme.TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Applies dark menu item styling to a {@link JMenuItem}.
     *
     * @param item the menu item to style
     * @since 0.1.0
     */
    private static void styleMenuItem(JMenuItem item) {
        item.setBackground(DarkTheme.PANEL_BG);
        item.setForeground(DarkTheme.TEXT);
    }

    /**
     * Formats a byte size into a human-readable string (B, KB, MB, GB).
     *
     * @param bytes the size in bytes
     * @return the formatted size string
     * @since 0.1.0
     */
    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Formats a {@link Duration} into a human-readable time string (h:mm:ss or m:ss).
     *
     * @param d the duration to format, may be {@code null}
     * @return the formatted duration string, or empty string if {@code null}
     * @since 0.1.0
     */
    static String formatDuration(Duration d) {
        if (d == null) return "";
        long totalSec = d.getSeconds();
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /**
     * Returns an emoji icon representing the given content item type,
     * matching the web variant's type indicators.
     *
     * @param type the content item type
     * @return an emoji string for the type
     * @since 0.1.0
     */
    static String typeIcon(ContentItemType type) {
        return switch (type) {
            case CONTAINER -> "📁";      // folder
            case AUDIO_ITEM -> "🎵";     // musical note
            case VIDEO_ITEM -> "🎬";      // clapper board
            case IMAGE_ITEM -> "🖼";      // framed picture
            case PLAYLIST_ITEM -> "📄";   // page facing up
            case TEXT_ITEM -> "📝";        // memo
            case GENERIC_ITEM -> "📄";     // page facing up
        };
    }

    /**
     * A breadcrumb navigation entry holding a container ID and display title.
     *
     * @param id    the container object ID
     * @param title the display title
     * @since 0.1.0
     */
    record BreadcrumbEntry(String id, String title) {
    }

    /**
     * Table model for the content item list with columns for icon, title,
     * creator, duration, size, and format.
     *
     * @since 0.1.0
     */
    static class ContentTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"", "Title", "Creator", "Duration", "Size", "Format"};
        private List<ContentItem> items = List.of();

        /**
         * Sets the items to display.
         *
         * @param items the content items
         * @since 0.1.0
         */
        void setItems(List<ContentItem> items) {
            this.items = items != null ? List.copyOf(items) : List.of();
            fireTableDataChanged();
        }

        /**
         * Returns the item at the given row.
         *
         * @param row the row index
         * @return the content item
         * @since 0.1.0
         */
        ContentItem getItemAt(int row) {
            return items.get(row);
        }

        /**
         * Returns all current items.
         *
         * @return the item list
         * @since 0.1.0
         */
        List<ContentItem> getItems() {
            return items;
        }

        @Override
        public int getRowCount() {
            return items.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ContentItem item = items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> typeIcon(item.getType());
                case 1 -> item.getTitle();
                case 2 -> item.getCreator() != null ? item.getCreator() : "";
                case 3 -> item.getDuration();
                case 4 -> item.getSize();
                case 5 -> item.getProtocolInfo() != null ? item.getProtocolInfo().contentFormat() : "";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 3 -> Duration.class;
                case 4 -> Long.class;
                default -> String.class;
            };
        }
    }

    /**
     * Renders {@link Duration} values as human-readable time strings with
     * dark theme alternating row background colours.
     *
     * @since 0.1.0
     */
    private static class DurationCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (isSelected) {
                c.setBackground(DarkTheme.SELECTED_BG);
                c.setForeground(DarkTheme.TEXT);
            } else {
                c.setBackground(row % 2 == 0 ? DarkTheme.BODY_BG : ALT_ROW);
                c.setForeground(DarkTheme.TEXT);
            }
            return c;
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Duration d) {
                setText(formatDuration(d));
            } else {
                setText("");
            }
        }
    }

    /**
     * Renders file size values as human-readable strings (KB/MB/GB) with
     * dark theme alternating row background colours.
     *
     * @since 0.1.0
     */
    private static class SizeCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (isSelected) {
                c.setBackground(DarkTheme.SELECTED_BG);
                c.setForeground(DarkTheme.TEXT);
            } else {
                c.setBackground(row % 2 == 0 ? DarkTheme.BODY_BG : ALT_ROW);
                c.setForeground(DarkTheme.TEXT);
            }
            return c;
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Long size && size > 0) {
                setText(formatSize(size));
            } else {
                setText("");
            }
        }
    }

    /**
     * Extracts a user-friendly error message from an exception, unwrapping
     * common wrapper exceptions like {@link java.util.concurrent.ExecutionException}
     * and {@link RuntimeException}.
     *
     * @param e the exception to extract a message from
     * @return a descriptive error message
     * @since 0.1.0
     */
    private static String extractErrorMessage(Throwable e) {
        Throwable cause = e;
        // Unwrap ExecutionException, RuntimeException wrappers
        while (cause.getCause() != null && (cause instanceof java.util.concurrent.ExecutionException
                || (cause instanceof RuntimeException && cause.getMessage() != null
                    && cause.getMessage().contains(cause.getCause().getMessage())))) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = cause.getClass().getSimpleName();
        }
        return msg;
    }

    /**
     * Shows a diagnostic error message in a dialog so the user is informed
     * about failures without blocking the UI thread.
     *
     * @param message the diagnostic message to display
     * @since 0.1.0
     */
    private void showDiagnosticMessage(String message) {
        // Log to stderr for debugging
        System.err.println("[MCC Diagnostic] " + message);
        // Show in the table area as an informational tooltip-like message
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    message,
                    "Content Browser Error",
                    JOptionPane.WARNING_MESSAGE);
        });
    }

    /**
     * Transfer handler for drag support of content items from the table
     * using {@link ContentItemTransferable}.
     *
     * <p>Creates a {@link ContentItemTransferable} carrying the full {@link ContentItem}
     * so that drop targets can access all metadata, not just the resource URL.
     *
     * @since 0.1.0
     */
    private class ContentTransferHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }

        /**
         * Creates a {@link ContentItemTransferable} for the selected table row.
         *
         * @param c the source component
         * @return a transferable wrapping the selected content item, or {@code null}
         * @since 0.1.0
         */
        @Override
        protected Transferable createTransferable(JComponent c) {
            int row = contentTable.getSelectedRow();
            if (row >= 0) {
                ContentItem item = tableModel.getItemAt(row);
                if (item.getResourceUrl() != null) {
                    return new ContentItemTransferable(item);
                }
            }
            return null;
        }
    }

    /**
     * Transfer handler for drag support of content items from the tree
     * using {@link ContentItemTransferable}.
     *
     * <p>Only non-container leaf nodes with a resource URL can be dragged.
     * The full {@link ContentItem} stored in the tree node is transferred
     * so that drop targets (renderer panel, local player) receive all metadata.
     *
     * @since 0.1.0
     */
    private class TreeContentTransferHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }

        /**
         * Creates a {@link ContentItemTransferable} for the selected tree node.
         *
         * @param c the source component (the JTree)
         * @return a transferable wrapping the selected content item, or {@code null}
         * @since 0.1.0
         */
        @Override
        protected Transferable createTransferable(JComponent c) {
            var path = contentTree.getSelectionPath();
            if (path != null) {
                var node = path.getLastPathComponent();
                if (node instanceof ContentTreeModel.ContentTreeNode treeNode
                        && !treeNode.isContainer()
                        && treeNode.getContentItem() != null
                        && treeNode.getContentItem().getResourceUrl() != null) {
                    return new ContentItemTransferable(treeNode.getContentItem());
                }
            }
            return null;
        }
    }
}
