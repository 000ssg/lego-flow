package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.DeviceListener;
import ssg.legoflow.upnp.controlpoint.DeviceProxy;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.mediarenderer.MediaRendererDevice;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Left-side panel displaying discovered UPnP devices in a tabbed layout with dark theme.
 *
 * <p>Provides three tabs: "Servers", "Renderers", and "All Devices", each showing
 * a device count in the tab label (e.g., "Servers (3)"). Tabs are styled with a dark
 * background and an accent-blue underline for the active tab, matching the web variant's
 * CSS tab design.
 *
 * <p>Each tab contains a {@link JList} with a custom {@link DeviceCellRenderer} that
 * renders device entries using emoji icons (💾 for servers, 🔊 for renderers,
 * 🌐 for routers, 📡 for generic devices), dark background colors, green left
 * border on selection, and hover highlighting. The "All Devices" tab additionally
 * displays the device type in muted text below the friendly name.
 *
 * <p>Device lists auto-update via the {@link DeviceListener} interface when devices
 * appear or disappear on the network.
 *
 * @since 1.0.0
 */
public class DeviceListPanel extends JPanel implements DeviceListener {

    /** Tab index for the Media Servers list. */
    private static final int TAB_SERVERS = 0;

    /** Tab index for the Media Renderers list. */
    private static final int TAB_RENDERERS = 1;

    /** Tab index for the All Devices list. */
    private static final int TAB_ALL = 2;

    /** Tab index for the Unrecognized (failed) devices list. */
    private static final int TAB_UNRECOGNIZED = 3;

    private final ControlPoint controlPoint;
    private final DefaultListModel<DeviceProxy> serverListModel;
    private final DefaultListModel<DeviceProxy> rendererListModel;
    private final DefaultListModel<DeviceProxy> allDevicesListModel;
    private final DefaultListModel<ControlPoint.FailedDevice> failedDevicesListModel;
    private final JList<DeviceProxy> serverList;
    private final JList<DeviceProxy> rendererList;
    private final JList<DeviceProxy> allDevicesList;
    private final JList<ControlPoint.FailedDevice> failedDevicesList;
    private final JTabbedPane tabbedPane;

    private final List<Consumer<MediaServerProxy>> serverSelectionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<MediaRendererProxy>> rendererSelectionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<DeviceProxy>> deviceSelectionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ControlPoint.FailedDevice>> failedDeviceSelectionListeners = new CopyOnWriteArrayList<>();

    private Frame parentFrame;

    /**
     * Creates a new device list panel bound to the given control point.
     *
     * <p>The panel is fully dark-themed using {@link DarkTheme} colors, with a titled
     * border, dark tabbed pane, custom device cell renderers with emoji icons, and a
     * styled refresh button. The control point's device listener is registered so that
     * device additions and removals automatically refresh the lists.
     *
     * @param controlPoint the control point providing device discovery
     * @since 1.0.0
     */
    public DeviceListPanel(ControlPoint controlPoint) {
        this.controlPoint = controlPoint;
        setLayout(new BorderLayout());
        setBackground(DarkTheme.PANEL_BG);
        setBorder(DarkTheme.panelBorder("Devices"));

        serverListModel = new DefaultListModel<>();
        rendererListModel = new DefaultListModel<>();
        allDevicesListModel = new DefaultListModel<>();
        failedDevicesListModel = new DefaultListModel<>();

        serverList = createDeviceList(serverListModel, false);
        rendererList = createDeviceList(rendererListModel, false);
        allDevicesList = createDeviceList(allDevicesListModel, true);
        failedDevicesList = createFailedDeviceList(failedDevicesListModel);

        serverList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                var selected = serverList.getSelectedValue();
                if (selected instanceof MediaServerProxy proxy) {
                    serverSelectionListeners.forEach(l -> l.accept(proxy));
                }
            }
        });

        rendererList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                var selected = rendererList.getSelectedValue();
                if (selected instanceof MediaRendererProxy proxy) {
                    rendererSelectionListeners.forEach(l -> l.accept(proxy));
                }
            }
        });

        allDevicesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                var selected = allDevicesList.getSelectedValue();
                if (selected != null) {
                    deviceSelectionListeners.forEach(l -> l.accept(selected));
                }
            }
        });

        // --- Dark themed tabbed pane ---
        tabbedPane = new JTabbedPane(JTabbedPane.TOP) {
            @Override
            public void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(DarkTheme.BODY_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        tabbedPane.setBackground(DarkTheme.BODY_BG);
        tabbedPane.setForeground(DarkTheme.TEXT);
        tabbedPane.setOpaque(true);
        tabbedPane.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        tabbedPane.setUI(new DarkTabbedPaneUI());

        tabbedPane.addTab("Servers (0)", createScrollPane(serverList));
        tabbedPane.addTab("Renderers (0)", createScrollPane(rendererList));
        tabbedPane.addTab("All (0)", createScrollPane(allDevicesList));
        tabbedPane.addTab("Unrecognized (0)", createScrollPane(failedDevicesList));

        // --- Toolbar with dark themed Refresh button ---
        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setBackground(DarkTheme.PANEL_BG);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER));

        var refreshButton = new JButton("Refresh");
        refreshButton.setBackground(DarkTheme.PANEL_BG);
        refreshButton.setForeground(DarkTheme.TEXT);
        refreshButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        refreshButton.setFocusPainted(false);
        refreshButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshButton.setToolTipText("Scan network for UPnP devices (M-SEARCH)");
        refreshButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                refreshButton.setBackground(DarkTheme.HOVER_BG);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                refreshButton.setBackground(DarkTheme.PANEL_BG);
            }
        });
        refreshButton.addActionListener(e -> {
            refreshButton.setEnabled(false);
            refreshButton.setText("Scanning...");
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    controlPoint.refresh();
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
                    return null;
                }

                @Override
                protected void done() {
                    refreshDeviceLists();
                    refreshButton.setEnabled(true);
                    refreshButton.setText("Refresh");
                }
            };
            worker.execute();
        });
        toolbar.add(refreshButton);

        add(toolbar, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        controlPoint.addDeviceListener(this);
        refreshDeviceLists();
    }

    /**
     * Sets the parent frame used as owner for dialog windows (e.g., device properties).
     *
     * @param frame the parent frame
     * @since 1.0.0
     */
    public void setParentFrame(Frame frame) {
        this.parentFrame = frame;
    }

    /**
     * Adds a listener notified when a media server is selected in the Servers tab.
     *
     * @param listener the selection listener receiving the selected {@link MediaServerProxy}
     * @since 1.0.0
     */
    public void addServerSelectionListener(Consumer<MediaServerProxy> listener) {
        serverSelectionListeners.add(listener);
    }

    /**
     * Adds a listener notified when a media renderer is selected in the Renderers tab.
     *
     * @param listener the selection listener receiving the selected {@link MediaRendererProxy}
     * @since 1.0.0
     */
    public void addRendererSelectionListener(Consumer<MediaRendererProxy> listener) {
        rendererSelectionListeners.add(listener);
    }

    /**
     * Adds a listener notified when any device is selected in the "All Devices" tab.
     *
     * @param listener the selection listener receiving the selected {@link DeviceProxy}
     * @since 1.0.0
     */
    public void addDeviceSelectionListener(Consumer<DeviceProxy> listener) {
        deviceSelectionListeners.add(listener);
    }

    /**
     * Adds a listener notified when a failed device is selected in the "Unrecognized" tab.
     *
     * @param listener the selection listener receiving the selected {@link ControlPoint.FailedDevice}
     * @since 1.0.0
     */
    public void addFailedDeviceSelectionListener(Consumer<ControlPoint.FailedDevice> listener) {
        failedDeviceSelectionListeners.add(listener);
    }

    /**
     * Returns the currently selected media server, or {@code null} if none is selected.
     *
     * @return the selected server proxy, or {@code null}
     * @since 1.0.0
     */
    public MediaServerProxy getSelectedServer() {
        var selected = serverList.getSelectedValue();
        return selected instanceof MediaServerProxy proxy ? proxy : null;
    }

    /**
     * Returns the currently selected media renderer, or {@code null} if none is selected.
     *
     * @return the selected renderer proxy, or {@code null}
     * @since 1.0.0
     */
    public MediaRendererProxy getSelectedRenderer() {
        var selected = rendererList.getSelectedValue();
        return selected instanceof MediaRendererProxy proxy ? proxy : null;
    }

    /**
     * Refreshes all device lists from the control point and updates tab labels with counts.
     *
     * <p>After repopulating the lists, auto-selects the first server and first renderer
     * if none is currently selected, triggering the corresponding selection listeners so
     * the content browser and playback controls are populated immediately. Tab labels are
     * updated to show the current device count, e.g., "Servers (3)".
     *
     * @since 1.0.0
     */
    public void refreshDeviceLists() {
        SwingUtilities.invokeLater(() -> {
            // Remember current selections so we can restore or auto-select
            var previousServer = serverList.getSelectedValue();
            var previousRenderer = rendererList.getSelectedValue();
            var previousDevice = allDevicesList.getSelectedValue();

            serverListModel.clear();
            for (MediaServerProxy server : controlPoint.discoverMediaServers()) {
                serverListModel.addElement(server);
            }

            rendererListModel.clear();
            for (MediaRendererProxy renderer : controlPoint.discoverMediaRenderers()) {
                rendererListModel.addElement(renderer);
            }

            allDevicesListModel.clear();
            for (DeviceProxy device : controlPoint.getDevices()) {
                allDevicesListModel.addElement(device);
            }

            failedDevicesListModel.clear();
            for (ControlPoint.FailedDevice fd : controlPoint.getFailedDevices()) {
                failedDevicesListModel.addElement(fd);
            }

            // Update tab labels with counts
            tabbedPane.setTitleAt(TAB_SERVERS, "Servers (" + serverListModel.getSize() + ")");
            tabbedPane.setTitleAt(TAB_RENDERERS, "Renderers (" + rendererListModel.getSize() + ")");
            tabbedPane.setTitleAt(TAB_ALL, "All (" + allDevicesListModel.getSize() + ")");
            tabbedPane.setTitleAt(TAB_UNRECOGNIZED, "Unrecognized (" + failedDevicesListModel.getSize() + ")");

            // Restore previous selection if the device is still present,
            // otherwise auto-select the first entry
            restoreOrAutoSelect(serverList, serverListModel, previousServer);
            restoreOrAutoSelect(rendererList, rendererListModel, previousRenderer);
            restoreOrAutoSelect(allDevicesList, allDevicesListModel, previousDevice);
        });
    }

    /**
     * Restores a previous selection in a list, or auto-selects the first
     * element if the previous selection is no longer present. This ensures
     * the content browser is populated immediately after device discovery.
     *
     * @param list              the list to update
     * @param model             the list model backing the list
     * @param previousSelection the previously selected device, or {@code null}
     */
    private static void restoreOrAutoSelect(JList<DeviceProxy> list,
                                            DefaultListModel<DeviceProxy> model,
                                            DeviceProxy previousSelection) {
        if (model.isEmpty()) return;

        if (previousSelection != null) {
            // Try to restore by matching UDN
            for (int i = 0; i < model.getSize(); i++) {
                if (model.get(i).getUdn().equals(previousSelection.getUdn())) {
                    list.setSelectedIndex(i);
                    return;
                }
            }
        }

        // No previous selection or it disappeared — auto-select first
        list.setSelectedIndex(0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Refreshes all device lists when a new device is discovered on the network.
     *
     * @since 1.0.0
     */
    @Override
    public void onDeviceAdded(DeviceProxy device) {
        refreshDeviceLists();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Refreshes all device lists when a device disappears from the network.
     *
     * @since 1.0.0
     */
    @Override
    public void onDeviceRemoved(DeviceProxy device) {
        refreshDeviceLists();
    }

    /**
     * Creates a dark-themed scroll pane wrapping the given component.
     *
     * @param view the component to wrap
     * @return a styled {@link JScrollPane}
     */
    private JScrollPane createScrollPane(Component view) {
        var scrollPane = new JScrollPane(view);
        scrollPane.setBackground(DarkTheme.BODY_BG);
        scrollPane.getViewport().setBackground(DarkTheme.BODY_BG);
        scrollPane.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        return scrollPane;
    }

    /**
     * Creates a dark-themed {@link JList} for device entries, with a custom cell
     * renderer, hover tracking, and a right-click context menu.
     *
     * @param model          the list model to bind
     * @param showDeviceType whether to display the device type label (for All Devices tab)
     * @return a styled device list
     */
    private JList<DeviceProxy> createDeviceList(DefaultListModel<DeviceProxy> model,
                                                boolean showDeviceType) {
        var list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(DarkTheme.BODY_BG);
        list.setForeground(DarkTheme.TEXT);
        list.setSelectionBackground(DarkTheme.SELECTED_BG);
        list.setSelectionForeground(DarkTheme.TEXT);
        list.setFixedCellHeight(-1); // allow variable heights for multi-line renderer

        var renderer = new DeviceCellRenderer(showDeviceType);
        list.setCellRenderer(renderer);

        // Hover tracking
        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index != renderer.getHoveredIndex()) {
                    renderer.setHoveredIndex(index);
                    list.repaint();
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                renderer.setHoveredIndex(-1);
                list.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(list, e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(list, e);
                }
            }
        });
        return list;
    }

    /**
     * Creates a dark-themed {@link JList} for failed (unrecognized) device entries,
     * with a custom cell renderer and selection listener.
     *
     * @param model the list model to bind
     * @return a styled failed device list
     */
    private JList<ControlPoint.FailedDevice> createFailedDeviceList(
            DefaultListModel<ControlPoint.FailedDevice> model) {
        var list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(DarkTheme.BODY_BG);
        list.setForeground(DarkTheme.TEXT);
        list.setSelectionBackground(DarkTheme.SELECTED_BG);
        list.setSelectionForeground(DarkTheme.TEXT);
        list.setFixedCellHeight(-1);

        list.setCellRenderer(new FailedDeviceCellRenderer());

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                var selected = list.getSelectedValue();
                if (selected != null) {
                    failedDeviceSelectionListeners.forEach(l -> l.accept(selected));
                }
            }
        });
        return list;
    }

    /**
     * Shows a right-click context menu with "Properties" and "Refresh" items.
     *
     * @param list the list that was right-clicked
     * @param e    the mouse event
     */
    private void showContextMenu(JList<DeviceProxy> list, MouseEvent e) {
        int index = list.locationToIndex(e.getPoint());
        if (index >= 0) {
            list.setSelectedIndex(index);
            var device = list.getModel().getElementAt(index);
            var popup = new JPopupMenu();
            popup.setBackground(DarkTheme.PANEL_BG);
            popup.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));

            var propertiesItem = new JMenuItem("Properties");
            propertiesItem.setBackground(DarkTheme.PANEL_BG);
            propertiesItem.setForeground(DarkTheme.TEXT);
            propertiesItem.addActionListener(ev -> {
                var dialog = new DevicePropertiesDialog(parentFrame, device);
                dialog.setVisible(true);
            });
            popup.add(propertiesItem);

            var refreshItem = new JMenuItem("Refresh");
            refreshItem.setBackground(DarkTheme.PANEL_BG);
            refreshItem.setForeground(DarkTheme.TEXT);
            refreshItem.addActionListener(ev -> refreshDeviceLists());
            popup.add(refreshItem);

            popup.show(list, e.getX(), e.getY());
        }
    }

    // =========================================================================
    // DeviceCellRenderer
    // =========================================================================

    /**
     * Custom cell renderer displaying emoji device-type icons and friendly names
     * with dark theme styling.
     *
     * <p>Rendering rules:
     * <ul>
     *   <li><b>Normal:</b> {@link DarkTheme#PANEL_BG} background, {@link DarkTheme#TEXT} foreground</li>
     *   <li><b>Selected:</b> {@link DarkTheme#SELECTED_BG} background with a 3px
     *       {@link DarkTheme#SUCCESS} green left border, {@link DarkTheme#TEXT} foreground</li>
     *   <li><b>Hover:</b> {@link DarkTheme#HOVER_BG} background</li>
     * </ul>
     *
     * <p>Emoji icons by device type:
     * <ul>
     *   <li>{@code 💾} — Media Server</li>
     *   <li>{@code 🔊} — Media Renderer</li>
     *   <li>{@code 🌐} — Internet Gateway / Router</li>
     *   <li>{@code 📡} — Generic / unknown device</li>
     * </ul>
     *
     * <p>When {@code showDeviceType} is {@code true} (used in the All Devices tab),
     * the device type URN is displayed below the friendly name in a smaller font
     * with {@link DarkTheme#MUTED_TEXT} colour.
     *
     * @since 1.0.0
     */
    static class DeviceCellRenderer extends JPanel implements ListCellRenderer<DeviceProxy> {

        /** Whether to show the device type label below the name. */
        private final boolean showDeviceType;

        /** The label displaying the emoji icon and friendly name. */
        private final JLabel nameLabel;

        /** The label displaying the device type URN (only when {@code showDeviceType} is true). */
        private final JLabel typeLabel;

        /** Index of the cell currently under the mouse cursor, or {@code -1}. */
        private int hoveredIndex = -1;

        /**
         * Creates a device cell renderer.
         *
         * @param showDeviceType {@code true} to display the device type below the name
         *                       (for the All Devices tab)
         * @since 1.0.0
         */
        DeviceCellRenderer(boolean showDeviceType) {
            this.showDeviceType = showDeviceType;
            setLayout(new BorderLayout());
            setOpaque(true);

            nameLabel = new JLabel();
            nameLabel.setForeground(DarkTheme.TEXT);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 13f));
            nameLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, showDeviceType ? 0 : 4, 8));
            add(nameLabel, BorderLayout.CENTER);

            if (showDeviceType) {
                typeLabel = new JLabel();
                typeLabel.setForeground(DarkTheme.MUTED_TEXT);
                typeLabel.setFont(typeLabel.getFont().deriveFont(Font.PLAIN, 10f));
                typeLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
                add(typeLabel, BorderLayout.SOUTH);
            } else {
                typeLabel = null;
            }
        }

        /**
         * Returns the index of the cell currently being hovered over.
         *
         * @return the hovered cell index, or {@code -1} if none
         * @since 1.0.0
         */
        int getHoveredIndex() {
            return hoveredIndex;
        }

        /**
         * Sets the index of the cell currently being hovered over and triggers
         * a repaint of the list.
         *
         * @param index the hovered cell index, or {@code -1} to clear
         * @since 1.0.0
         */
        void setHoveredIndex(int index) {
            this.hoveredIndex = index;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Renders a device cell with an emoji icon, friendly name, optional device
         * type label, and dark theme colours including selection and hover states.
         *
         * @since 1.0.0
         */
        @Override
        public Component getListCellRendererComponent(JList<? extends DeviceProxy> list,
                                                      DeviceProxy value, int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            String icon = resolveIcon(value);
            nameLabel.setText(icon + "  " + value.getFriendlyName());
            nameLabel.setForeground(DarkTheme.TEXT);

            if (showDeviceType && typeLabel != null) {
                typeLabel.setText(value.getDeviceType());
                typeLabel.setForeground(DarkTheme.MUTED_TEXT);
            }

            setToolTipText(value.getDeviceType());

            // --- Background and border based on state ---
            if (isSelected) {
                setBackground(DarkTheme.SELECTED_BG);
                // 3px green left border indicating selection
                setBorder(new MatteBorder(0, 3, 0, 0, DarkTheme.SUCCESS));
            } else if (index == hoveredIndex) {
                setBackground(DarkTheme.HOVER_BG);
                setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
            } else {
                setBackground(DarkTheme.PANEL_BG);
                setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
            }

            return this;
        }

        /**
         * Resolves the emoji icon for a device based on its type.
         *
         * @param device the device proxy
         * @return an emoji string representing the device type
         * @since 1.0.0
         */
        private static String resolveIcon(DeviceProxy device) {
            if (device instanceof MediaServerProxy) {
                return "💾"; // 💾
            } else if (device instanceof MediaRendererProxy) {
                return "🔊"; // 🔊
            } else {
                String type = device.getDeviceType();
                if (type != null && type.toLowerCase().contains("internetgateway")) {
                    return "🌐"; // 🌐
                }
                return "📡"; // 📡
            }
        }
    }

    // =========================================================================
    // FailedDeviceCellRenderer
    // =========================================================================

    /**
     * Custom cell renderer for failed (unrecognized) devices, displaying a warning
     * icon, the UDN, and the error message in muted red text.
     *
     * @since 1.0.0
     */
    static class FailedDeviceCellRenderer extends JPanel
            implements ListCellRenderer<ControlPoint.FailedDevice> {

        private final JLabel nameLabel;
        private final JLabel errorLabel;

        FailedDeviceCellRenderer() {
            setLayout(new BorderLayout());
            setOpaque(true);

            nameLabel = new JLabel();
            nameLabel.setForeground(DarkTheme.TEXT);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 13f));
            nameLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
            add(nameLabel, BorderLayout.CENTER);

            errorLabel = new JLabel();
            errorLabel.setForeground(DarkTheme.ERROR);
            errorLabel.setFont(errorLabel.getFont().deriveFont(Font.PLAIN, 10f));
            errorLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
            add(errorLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ControlPoint.FailedDevice> list,
                ControlPoint.FailedDevice value, int index,
                boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText("⚠  " + value.udn());
            String error = value.errorMessage();
            if (error != null && error.length() > 80) {
                error = error.substring(0, 77) + "...";
            }
            errorLabel.setText(error != null ? error : "Unknown error");

            if (isSelected) {
                setBackground(DarkTheme.SELECTED_BG);
                setBorder(new MatteBorder(0, 3, 0, 0, DarkTheme.WARNING));
            } else {
                setBackground(DarkTheme.PANEL_BG);
                setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
            }
            return this;
        }
    }

    // =========================================================================
    // DarkTabbedPaneUI
    // =========================================================================

    /**
     * Custom {@link javax.swing.plaf.basic.BasicTabbedPaneUI} that renders tabs with
     * dark backgrounds and an accent-blue underline for the selected tab, matching
     * the web variant's CSS tab styling.
     *
     * @since 1.0.0
     */
    private static class DarkTabbedPaneUI extends javax.swing.plaf.basic.BasicTabbedPaneUI {

        /**
         * {@inheritDoc}
         *
         * @since 1.0.0
         */
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(0, 0, 0, 0);
            contentBorderInsets = new Insets(1, 0, 0, 0);
            tabInsets = new Insets(8, 16, 8, 16);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
        }

        /**
         * {@inheritDoc}
         *
         * @since 1.0.0
         */
        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            g.setColor(isSelected ? DarkTheme.PANEL_BG : DarkTheme.BODY_BG);
            g.fillRect(x, y, w, h);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Paints a 2px accent-blue underline for the selected tab, or a border-coloured
         * underline for unselected tabs.
         *
         * @since 1.0.0
         */
        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
            if (isSelected) {
                g.setColor(DarkTheme.ACCENT);
                g.fillRect(x, y + h - 2, w, 2);
            } else {
                g.setColor(DarkTheme.BORDER);
                g.fillRect(x, y + h - 1, w, 1);
            }
        }

        /**
         * {@inheritDoc}
         *
         * @since 1.0.0
         */
        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            g.setColor(DarkTheme.BORDER);
            var bounds = tabPane.getBounds();
            int y = rects[0].y + rects[0].height;
            g.drawLine(0, y, bounds.width, y);
        }

        /**
         * {@inheritDoc}
         *
         * @since 1.0.0
         */
        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement,
                                           Rectangle[] rects, int tabIndex,
                                           Rectangle iconRect, Rectangle textRect,
                                           boolean isSelected) {
            // No focus indicator — clean appearance
        }

        /**
         * {@inheritDoc}
         *
         * @since 1.0.0
         */
        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                                 int tabIndex, String title, Rectangle textRect,
                                 boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(isSelected ? DarkTheme.TEXT : DarkTheme.SECONDARY_TEXT);
            g2.setFont(font);
            g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
        }
    }
}
