package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Main Swing application frame for the Media Control Center.
 *
 * <p>Provides a complete DLNA control point GUI with device discovery,
 * content browsing, playback control, and local playback. The layout
 * mirrors the web variant with a three-column flat panel design using
 * the {@link DarkTheme} colour scheme: a fixed-width device panel on
 * the left, a flexible content browser in the centre, and a right
 * column containing the now-playing display, playback controls, and
 * local player.
 *
 * <p>On startup, creates a {@link ControlPoint} and starts device discovery.
 * On shutdown, gracefully stops the control point and releases resources.
 *
 * @since 0.1.0
 */
public class MediaControlCenter extends JFrame {

    private final ControlPoint controlPoint;
    private final DeviceListPanel deviceListPanel;
    private final ContentBrowserPanel contentBrowserPanel;
    private final DeviceDetailsPanel deviceDetailsPanel;
    private final NowPlayingPanel nowPlayingPanel;
    private final PlaybackControlPanel playbackControlPanel;
    private final LocalPlaybackEngine localPlaybackEngine;
    private final StatusBar statusBar;
    private JPanel leftBottomCards;

    /**
     * Creates a new Media Control Center with the given control point.
     *
     * <p>Applies the {@link DarkTheme} first, then creates all child
     * components and assembles the web-variant layout with header,
     * three-column main area, and footer.
     *
     * @param controlPoint the UPnP control point for device discovery and control
     * @since 0.1.0
     */
    public MediaControlCenter(ControlPoint controlPoint) {
        super("Lego Flow — Media Control Center");

        // Apply dark theme FIRST, before creating any components
        DarkTheme.apply();

        this.controlPoint = controlPoint;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1200, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(DarkTheme.BODY_BG);

        // Components
        deviceListPanel = new DeviceListPanel(controlPoint);
        deviceListPanel.setParentFrame(this);
        contentBrowserPanel = new ContentBrowserPanel();
        deviceDetailsPanel = new DeviceDetailsPanel();
        nowPlayingPanel = new NowPlayingPanel();
        playbackControlPanel = new PlaybackControlPanel(controlPoint);
        localPlaybackEngine = new LocalPlaybackEngine();
        statusBar = new StatusBar(controlPoint);

        // Left bottom card panel: switches between content browser and device details
        leftBottomCards = new JPanel(new CardLayout());
        leftBottomCards.add(contentBrowserPanel, "browser");
        leftBottomCards.add(deviceDetailsPanel, "details");

        // Wire up interactions
        wireEvents();

        // Layout
        buildLayout();
        buildMenuBar();

        // Shutdown hook
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        // Discovery is triggered externally by the caller (e.g. MediaControlCenterApp)
        // after the UI is visible. The DeviceListener on DeviceListPanel will auto-refresh
        // when devices are discovered.
    }

    /**
     * Returns the control point.
     *
     * @return the control point
     * @since 0.1.0
     */
    public ControlPoint getControlPoint() {
        return controlPoint;
    }

    /**
     * Returns the device list panel.
     *
     * @return the device list panel
     * @since 0.1.0
     */
    public DeviceListPanel getDeviceListPanel() {
        return deviceListPanel;
    }

    /**
     * Returns the content browser panel.
     *
     * @return the content browser panel
     * @since 0.1.0
     */
    public ContentBrowserPanel getContentBrowserPanel() {
        return contentBrowserPanel;
    }

    /**
     * Returns the device details panel.
     *
     * @return the device details panel
     * @since 0.1.0
     */
    public DeviceDetailsPanel getDeviceDetailsPanel() {
        return deviceDetailsPanel;
    }

    /**
     * Returns the now playing panel.
     *
     * @return the now playing panel
     * @since 0.1.0
     */
    public NowPlayingPanel getNowPlayingPanel() {
        return nowPlayingPanel;
    }

    /**
     * Returns the playback control panel.
     *
     * @return the playback control panel
     * @since 0.1.0
     */
    public PlaybackControlPanel getPlaybackControlPanel() {
        return playbackControlPanel;
    }

    /**
     * Returns the local playback engine.
     *
     * @return the local playback engine
     * @since 0.1.0
     */
    public LocalPlaybackEngine getLocalPlaybackEngine() {
        return localPlaybackEngine;
    }

    /**
     * Returns the status bar.
     *
     * @return the status bar
     * @since 0.1.0
     */
    public StatusBar getStatusBar() {
        return statusBar;
    }

    /**
     * Refreshes device discovery.
     *
     * @since 0.1.0
     */
    public void refreshDevices() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                controlPoint.refresh();
                return null;
            }

            @Override
            protected void done() {
                deviceListPanel.refreshDeviceLists();
                playbackControlPanel.updateRendererList(controlPoint.discoverMediaRenderers());
            }
        };
        worker.execute();
    }

    /**
     * Performs graceful shutdown: stops polling, stops the control point,
     * disposes the frame, cleans up system tray, and exits the JVM.
     *
     * @since 0.1.0
     */
    public void shutdown() {
        nowPlayingPanel.stopPolling();
        localPlaybackEngine.stop();
        controlPoint.stop();

        // Remove all tray icons to prevent AWT from keeping the JVM alive
        if (SystemTray.isSupported()) {
            var tray = SystemTray.getSystemTray();
            for (var icon : tray.getTrayIcons()) {
                tray.remove(icon);
            }
        }

        dispose();
        System.exit(0);
    }

    private void wireEvents() {
        // Server selection -> update content browser and switch to browser card
        deviceListPanel.addServerSelectionListener(server -> {
            contentBrowserPanel.setServer(server);
            statusBar.setCurrentServer(server.getFriendlyName());
            ((CardLayout) leftBottomCards.getLayout()).show(leftBottomCards, "browser");
        });

        // Renderer selection -> update controls and now-playing
        deviceListPanel.addRendererSelectionListener(renderer -> {
            playbackControlPanel.setRenderer(renderer);
            nowPlayingPanel.setRenderer(renderer);
            statusBar.setCurrentRenderer(renderer.getFriendlyName());
            playbackControlPanel.updateRendererList(controlPoint.discoverMediaRenderers());
        });

        // All Devices tab selection -> show per-type details
        deviceListPanel.addDeviceSelectionListener(device -> {
            if (device instanceof MediaServerProxy server) {
                // Server: show content browser
                contentBrowserPanel.setServer(server);
                statusBar.setCurrentServer(server.getFriendlyName());
                ((CardLayout) leftBottomCards.getLayout()).show(leftBottomCards, "browser");
            } else {
                // Renderer or generic device: show device details panel
                deviceDetailsPanel.showDevice(device);
                ((CardLayout) leftBottomCards.getLayout()).show(leftBottomCards, "details");
                if (device instanceof MediaRendererProxy renderer) {
                    playbackControlPanel.setRenderer(renderer);
                    nowPlayingPanel.setRenderer(renderer);
                    statusBar.setCurrentRenderer(renderer.getFriendlyName());
                    playbackControlPanel.updateRendererList(controlPoint.discoverMediaRenderers());
                }
            }
        });

        // Unrecognized (failed) device selection -> show error details
        deviceListPanel.addFailedDeviceSelectionListener(failedDevice -> {
            deviceDetailsPanel.showFailedDevice(failedDevice);
            ((CardLayout) leftBottomCards.getLayout()).show(leftBottomCards, "details");
        });

        // Renderer combo selection
        playbackControlPanel.setRendererSelectedCallback(renderer -> {
            nowPlayingPanel.setRenderer(renderer);
            statusBar.setCurrentRenderer(renderer.getFriendlyName());
        });

        // Play on renderer from content browser (double-click / context menu)
        contentBrowserPanel.setPlayOnRendererAction(item -> {
            playbackControlPanel.playItem(item);
            nowPlayingPanel.setNowPlaying(item);
        });

        // Play locally from content browser (context menu)
        contentBrowserPanel.setPlayLocallyAction(item -> {
            localPlaybackEngine.play(item);
            nowPlayingPanel.setNowPlaying(item);
        });

        // Wire PlaybackControlPanel to update NowPlayingPanel on drop
        playbackControlPanel.setNowPlayingPanel(nowPlayingPanel);

        // Wire local playback engine to control panel for transport/volume control
        playbackControlPanel.setLocalPlaybackEngine(localPlaybackEngine);

        // Drop on NowPlayingPanel -> play on renderer
        nowPlayingPanel.setDropAction(item -> {
            playbackControlPanel.playItem(item);
            nowPlayingPanel.setNowPlaying(item);
        });

        // Media support info -> status bar
        localPlaybackEngine.setMediaSupportInfoListener(info ->
                statusBar.setMediaSupportInfo(info));

        // Drop on local player -> play locally
        localPlaybackEngine.setDropAction(item -> {
            localPlaybackEngine.play(item);
            nowPlayingPanel.setNowPlaying(item);
            // If dropped from NowPlayingPanel, synchronize position
            var rendererItem = nowPlayingPanel.getCurrentItem();
            if (rendererItem != null && rendererItem.equals(item)) {
                var renderer = playbackControlPanel.getRenderer();
                if (renderer != null) {
                    Thread.startVirtualThread(() -> {
                        try {
                            var posInfo = renderer.getPosition();
                            localPlaybackEngine.synchronizeWithRemote(posInfo.relTime());
                        } catch (Exception e) {
                            // Ignore sync errors
                        }
                    });
                }
            }
        });
    }

    private void buildLayout() {
        var content = getContentPane();
        content.setLayout(new BorderLayout());
        content.setBackground(DarkTheme.BODY_BG);

        // ---- Header ----
        var header = new JPanel(new BorderLayout());
        header.setBackground(DarkTheme.PANEL_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));

        var titleLabel = new JLabel("Media Control Center");
        titleLabel.setForeground(DarkTheme.TEXT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        header.add(titleLabel, BorderLayout.WEST);

        var badge = new JLabel("Connected");
        badge.setForeground(DarkTheme.SUCCESS);
        badge.setFont(badge.getFont().deriveFont(Font.PLAIN, 12f));
        header.add(badge, BorderLayout.EAST);

        content.add(header, BorderLayout.NORTH);

        // ---- Main three-column area ----
        var mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(DarkTheme.BODY_BG);
        var gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;

        // Left column: device list (fixed 250px)
        deviceListPanel.setPreferredSize(new Dimension(250, 0));
        deviceListPanel.setMinimumSize(new Dimension(250, 0));
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.weighty = 1.0;
        mainPanel.add(deviceListPanel, gbc);

        // Separator between left and center
        gbc.gridx = 1;
        gbc.weightx = 0;
        mainPanel.add(createVerticalSeparator(), gbc);

        // Center column: content browser / device details (flex)
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        mainPanel.add(leftBottomCards, gbc);

        // Separator between center and right
        gbc.gridx = 3;
        gbc.weightx = 0;
        mainPanel.add(createVerticalSeparator(), gbc);

        // Right column: use JSplitPane so controls are always visible and user can resize
        var rightColumn = new JPanel(new BorderLayout());
        rightColumn.setBackground(DarkTheme.BODY_BG);
        rightColumn.setPreferredSize(new Dimension(300, 0));
        rightColumn.setMinimumSize(new Dimension(300, 0));

        // Top part: now playing (scrollable)
        var nowPlayingScroll = new JScrollPane(nowPlayingPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        nowPlayingScroll.setBorder(null);
        nowPlayingScroll.getViewport().setBackground(DarkTheme.BODY_BG);
        nowPlayingScroll.setMinimumSize(new Dimension(0, 80));

        // Bottom part: playback controls + local player in a fixed-height panel
        var bottomPanel = new JPanel(new BorderLayout(0, 2));
        bottomPanel.setBackground(DarkTheme.BODY_BG);
        bottomPanel.add(playbackControlPanel, BorderLayout.NORTH);
        var localPlayerWrapper = new JPanel(new BorderLayout());
        localPlayerWrapper.setBackground(DarkTheme.BODY_BG);
        localPlayerWrapper.add(localPlaybackEngine.getVideoPanel(), BorderLayout.CENTER);
        bottomPanel.add(localPlayerWrapper, BorderLayout.CENTER);
        // Ensure bottom panel has enough height for controls + local player
        bottomPanel.setMinimumSize(new Dimension(0, 250));
        bottomPanel.setPreferredSize(new Dimension(300, 300));

        // Split pane: now playing on top, controls on bottom
        var rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, nowPlayingScroll, bottomPanel);
        rightSplit.setBackground(DarkTheme.BODY_BG);
        rightSplit.setBorder(null);
        rightSplit.setDividerSize(4);
        rightSplit.setResizeWeight(0.5); // share space equally
        rightSplit.setContinuousLayout(true);

        rightColumn.add(rightSplit, BorderLayout.CENTER);

        gbc.gridx = 4;
        gbc.weightx = 0;
        mainPanel.add(rightColumn, gbc);

        content.add(mainPanel, BorderLayout.CENTER);

        // ---- Footer ----
        var footer = new JPanel(new BorderLayout());
        footer.setBackground(DarkTheme.PANEL_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)));

        var footerLeft = new JLabel("Lego Flow MCC");
        footerLeft.setForeground(DarkTheme.MUTED_TEXT);
        footerLeft.setFont(footerLeft.getFont().deriveFont(Font.PLAIN, 11f));
        footer.add(footerLeft, BorderLayout.WEST);

        footer.add(statusBar, BorderLayout.EAST);

        content.add(footer, BorderLayout.SOUTH);
    }

    /**
     * Creates a 1px-wide vertical separator panel coloured with the theme border.
     */
    private static JPanel createVerticalSeparator() {
        var sep = new JPanel();
        sep.setBackground(DarkTheme.BORDER);
        sep.setPreferredSize(new Dimension(1, 0));
        sep.setMinimumSize(new Dimension(1, 0));
        sep.setMaximumSize(new Dimension(1, Integer.MAX_VALUE));
        return sep;
    }

    /**
     * Creates a 1px-tall horizontal separator panel coloured with the theme border.
     */
    private static JPanel createHorizontalSeparator() {
        var sep = new JPanel();
        sep.setBackground(DarkTheme.BORDER);
        sep.setPreferredSize(new Dimension(0, 1));
        sep.setMinimumSize(new Dimension(0, 1));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private void buildMenuBar() {
        var menuBar = new JMenuBar();
        menuBar.setBackground(DarkTheme.PANEL_BG);
        menuBar.setForeground(DarkTheme.TEXT);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER));

        // File menu
        var fileMenu = new JMenu("File");
        fileMenu.setForeground(DarkTheme.TEXT);
        var exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> shutdown());
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // View menu
        var viewMenu = new JMenu("View");
        viewMenu.setForeground(DarkTheme.TEXT);
        var refreshItem = new JMenuItem("Refresh Devices");
        refreshItem.addActionListener(e -> refreshDevices());
        viewMenu.add(refreshItem);
        menuBar.add(viewMenu);

        // Diagnostics menu
        var diagMenu = new JMenu("Diagnostics");
        diagMenu.setForeground(DarkTheme.TEXT);

        var logToggle = new JCheckBoxMenuItem("Enable UPnP Logging");
        logToggle.setSelected(controlPoint.getMessageLog().isEnabled());
        logToggle.addActionListener(e -> {
            controlPoint.getMessageLog().setEnabled(logToggle.isSelected());
            if (logToggle.isSelected()) {
                statusBar.setStatus("UPnP message logging enabled");
            } else {
                statusBar.setStatus("UPnP message logging disabled");
            }
        });
        diagMenu.add(logToggle);

        var showLogItem = new JMenuItem("Show Log Window");
        showLogItem.addActionListener(e -> showLogWindow());
        diagMenu.add(showLogItem);

        diagMenu.addSeparator();

        var clearLogItem = new JMenuItem("Clear Log");
        clearLogItem.addActionListener(e -> {
            controlPoint.getMessageLog().clear();
            statusBar.setStatus("Log cleared");
        });
        diagMenu.add(clearLogItem);

        menuBar.add(diagMenu);

        // Help menu
        var helpMenu = new JMenu("Help");
        helpMenu.setForeground(DarkTheme.TEXT);
        var aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Lego Flow — Media Control Center\n\n"
                        + "A UPnP/DLNA control point demo application.\n"
                        + "Part of the Lego Flow framework.\n\n"
                        + "Version 1.0.0",
                "About Media Control Center",
                JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    /**
     * Opens a floating log window displaying captured UPnP protocol messages.
     *
     * @since 0.1.0
     */
    private void showLogWindow() {
        var logFrame = new JFrame("UPnP Message Log");
        logFrame.setSize(800, 600);
        logFrame.setLocationRelativeTo(this);
        logFrame.getContentPane().setBackground(DarkTheme.BODY_BG);

        var textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setBackground(DarkTheme.BODY_BG);
        textArea.setForeground(DarkTheme.TEXT);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setCaretColor(DarkTheme.TEXT);

        // Populate with existing entries
        var log = controlPoint.getMessageLog();
        textArea.setText(log.exportAll());

        // Live updates
        var listener = new java.util.function.Consumer<ssg.legoflow.upnp.controlpoint.UpnpMessageLog.LogEntry>() {
            @Override
            public void accept(ssg.legoflow.upnp.controlpoint.UpnpMessageLog.LogEntry entry) {
                SwingUtilities.invokeLater(() -> {
                    textArea.append(entry.format() + "\n\n");
                    textArea.setCaretPosition(textArea.getDocument().getLength());
                });
            }
        };
        log.addListener(listener);
        logFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log.removeListener(listener);
            }
        });

        var scrollPane = new JScrollPane(textArea);
        scrollPane.getViewport().setBackground(DarkTheme.BODY_BG);
        scrollPane.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));

        // Toolbar
        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setBackground(DarkTheme.PANEL_BG);

        var clearBtn = new JButton("Clear");
        clearBtn.setBackground(DarkTheme.PANEL_BG);
        clearBtn.setForeground(DarkTheme.TEXT);
        clearBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        clearBtn.addActionListener(e -> {
            log.clear();
            textArea.setText("");
        });
        toolbar.add(clearBtn);

        var copyBtn = new JButton("Copy All");
        copyBtn.setBackground(DarkTheme.PANEL_BG);
        copyBtn.setForeground(DarkTheme.TEXT);
        copyBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        copyBtn.addActionListener(e -> {
            textArea.selectAll();
            textArea.copy();
            textArea.setCaretPosition(textArea.getDocument().getLength());
            statusBar.setStatus("Log copied to clipboard");
        });
        toolbar.add(copyBtn);

        var enableLabel = new JLabel();
        enableLabel.setForeground(log.isEnabled() ? DarkTheme.SUCCESS : DarkTheme.ERROR);
        enableLabel.setText(log.isEnabled() ? "  ● Logging ON" : "  ● Logging OFF — enable via Diagnostics menu");
        toolbar.add(enableLabel);

        logFrame.add(toolbar, BorderLayout.NORTH);
        logFrame.add(scrollPane, BorderLayout.CENTER);
        logFrame.setVisible(true);
    }
}
