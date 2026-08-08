package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import ssg.legoflow.upnp.mediarenderer.PlaybackListener;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import ssg.legoflow.upnp.mediaserver.ContentItem;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Transport and volume control panel with circular playback buttons, seek slider,
 * volume slider, mute toggle, and renderer selector, styled with the MCC dark theme.
 *
 * <p>All controls delegate to the currently selected {@link MediaRendererProxy}.
 * Buttons are enabled or disabled based on the current transport state.
 * Keyboard shortcuts are bound for common actions: Space for play/pause,
 * Left/Right arrows for seeking, Up/Down arrows for volume.</p>
 *
 * <p>Transport buttons use Unicode symbols rendered inside {@link DarkTheme}
 * circular buttons to match the web variant:</p>
 * <ul>
 *   <li>{@code ⏮} Previous</li>
 *   <li>{@code ⏪} Rewind</li>
 *   <li>{@code ▶} / {@code ⏸} Play / Pause toggle</li>
 *   <li>{@code ⏹} Stop</li>
 *   <li>{@code ⏩} Forward</li>
 *   <li>{@code ⏭} Next</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class PlaybackControlPanel extends JPanel implements PlaybackListener {

    private static final String PLAY_SYMBOL = "▶";
    private static final String PAUSE_SYMBOL = "⏸";
    private static final String STOP_SYMBOL = "⏹";
    private static final String PREVIOUS_SYMBOL = "⏮";
    private static final String NEXT_SYMBOL = "⏭";
    private static final String REWIND_SYMBOL = "⏪";
    private static final String FORWARD_SYMBOL = "⏩";
    private static final String MUTE_SYMBOL = "🔇";
    private static final String UNMUTE_SYMBOL = "🔊";

    private final JButton previousButton;
    private final JButton rewindButton;
    private final JButton playPauseButton;
    private final JButton stopButton;
    private final JButton forwardButton;
    private final JButton nextButton;
    private final JSlider positionSlider;
    private final JSlider volumeSlider;
    private final JButton muteButton;
    private final JLabel volumeValueLabel;
    private final JComboBox<RendererEntry> rendererCombo;

    private volatile MediaRendererProxy currentRenderer;
    private volatile TransportState currentState = TransportState.NO_MEDIA_PRESENT;
    private volatile boolean sliderDragging;
    private volatile boolean volumeDragging;
    private volatile Duration trackDuration = Duration.ZERO;

    private Consumer<MediaRendererProxy> rendererSelectedCallback;
    private NowPlayingPanel nowPlayingPanel;
    private LocalPlaybackEngine localPlaybackEngine;

    /**
     * Creates a new playback control panel with dark-themed circular transport
     * buttons, volume slider, mute toggle, position slider, and renderer selector.
     *
     * @param controlPoint the control point for discovering renderers
     * @since 0.1.0
     */
    public PlaybackControlPanel(ControlPoint controlPoint) {
        setLayout(new BorderLayout(4, 4));
        setBackground(DarkTheme.PANEL_BG);
        setBorder(DarkTheme.panelBorder("Playback Controls"));

        // Transport buttons — circular with Unicode symbols
        previousButton = DarkTheme.createCircleButton(PREVIOUS_SYMBOL, "Previous track");
        rewindButton = DarkTheme.createCircleButton(REWIND_SYMBOL, "Rewind 10 seconds");
        playPauseButton = DarkTheme.createCircleButton(PLAY_SYMBOL, "Play / Pause (Space)");
        stopButton = DarkTheme.createCircleButton(STOP_SYMBOL, "Stop");
        forwardButton = DarkTheme.createCircleButton(FORWARD_SYMBOL, "Forward 10 seconds");
        nextButton = DarkTheme.createCircleButton(NEXT_SYMBOL, "Next track");

        previousButton.addActionListener(e -> doAction(MediaRendererProxy::previous));
        rewindButton.addActionListener(e -> seekRelative(-10));
        playPauseButton.addActionListener(e -> togglePlayPause());
        stopButton.addActionListener(e -> doStopAction());
        forwardButton.addActionListener(e -> seekRelative(10));
        nextButton.addActionListener(e -> doAction(MediaRendererProxy::next));

        var transportPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        transportPanel.setBackground(DarkTheme.PANEL_BG);
        transportPanel.add(previousButton);
        transportPanel.add(rewindButton);
        transportPanel.add(playPauseButton);
        transportPanel.add(stopButton);
        transportPanel.add(forwardButton);
        transportPanel.add(nextButton);

        // Position slider — dark themed
        positionSlider = new JSlider(0, 1000, 0);
        positionSlider.setBackground(DarkTheme.BODY_BG);
        positionSlider.setForeground(DarkTheme.ACCENT);
        positionSlider.setEnabled(false);
        positionSlider.addChangeListener(this::onPositionSliderChanged);

        // Volume controls — dark themed slider with value display
        volumeSlider = DarkTheme.createVolumeSlider();
        volumeSlider.setPreferredSize(new Dimension(120, volumeSlider.getPreferredSize().height));
        volumeSlider.addChangeListener(this::onVolumeSliderChanged);

        muteButton = DarkTheme.createSmallCircleButton(UNMUTE_SYMBOL, "Mute / Unmute");
        muteButton.addActionListener(e -> toggleMute());

        var volLabel = new JLabel("Vol:");
        volLabel.setForeground(DarkTheme.SECONDARY_TEXT);

        volumeValueLabel = new JLabel(String.valueOf(volumeSlider.getValue()));
        volumeValueLabel.setForeground(DarkTheme.SECONDARY_TEXT);
        volumeValueLabel.setPreferredSize(new Dimension(24, volumeValueLabel.getPreferredSize().height));
        volumeValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        var volumePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        volumePanel.setBackground(DarkTheme.PANEL_BG);
        volumePanel.add(volLabel);
        volumePanel.add(volumeSlider);
        volumePanel.add(volumeValueLabel);
        volumePanel.add(muteButton);

        // Renderer selector — dark themed
        rendererCombo = new JComboBox<>();
        rendererCombo.setBackground(DarkTheme.BODY_BG);
        rendererCombo.setForeground(DarkTheme.TEXT);
        rendererCombo.addActionListener(e -> {
            var selected = (RendererEntry) rendererCombo.getSelectedItem();
            if (selected != null && selected.proxy() != null) {
                setRenderer(selected.proxy());
                if (rendererSelectedCallback != null) {
                    rendererSelectedCallback.accept(selected.proxy());
                }
            }
        });

        var rendererLabel = new JLabel("Renderer: ");
        rendererLabel.setForeground(DarkTheme.TEXT);

        var rendererPanel = new JPanel(new BorderLayout(4, 0));
        rendererPanel.setBackground(DarkTheme.PANEL_BG);
        rendererPanel.add(rendererLabel, BorderLayout.WEST);
        rendererPanel.add(rendererCombo, BorderLayout.CENTER);

        // Drop support on the whole control panel
        setTransferHandler(new PlaybackControlTransferHandler());

        // Layout
        var topRow = new JPanel(new BorderLayout());
        topRow.setBackground(DarkTheme.PANEL_BG);
        topRow.add(transportPanel, BorderLayout.CENTER);
        topRow.add(volumePanel, BorderLayout.EAST);

        var bottomRow = new JPanel(new BorderLayout(8, 0));
        bottomRow.setBackground(DarkTheme.PANEL_BG);
        bottomRow.add(positionSlider, BorderLayout.CENTER);
        bottomRow.add(rendererPanel, BorderLayout.SOUTH);

        add(topRow, BorderLayout.NORTH);
        add(bottomRow, BorderLayout.CENTER);

        updateButtonStates();
        setupKeyBindings();
    }

    /**
     * Sets the callback invoked when a renderer is selected from the combo box.
     *
     * @param callback the callback
     * @since 0.1.0
     */
    public void setRendererSelectedCallback(Consumer<MediaRendererProxy> callback) {
        this.rendererSelectedCallback = callback;
    }

    /**
     * Sets the active media renderer for playback control.
     *
     * @param renderer the renderer proxy, or {@code null} to clear
     * @since 0.1.0
     */
    public void setRenderer(MediaRendererProxy renderer) {
        this.currentRenderer = renderer;
        if (renderer != null) {
            renderer.subscribeTransportEvents(this);
            try {
                currentState = renderer.getTransportState();
                int vol = renderer.getVolume();
                SwingUtilities.invokeLater(() -> {
                    volumeSlider.setValue(vol);
                    volumeValueLabel.setText(String.valueOf(vol));
                    updateButtonStates();
                });
            } catch (Exception e) {
                // Ignore initial state query errors
            }
        } else {
            currentState = TransportState.NO_MEDIA_PRESENT;
            SwingUtilities.invokeLater(this::updateButtonStates);
        }
    }

    /**
     * Returns the currently selected renderer.
     *
     * @return the renderer proxy, or {@code null}
     * @since 0.1.0
     */
    public MediaRendererProxy getRenderer() {
        return currentRenderer;
    }

    /**
     * Updates the renderer combo box with the given list of renderers.
     *
     * @param renderers the available renderers
     * @since 0.1.0
     */
    public void updateRendererList(List<MediaRendererProxy> renderers) {
        SwingUtilities.invokeLater(() -> {
            var selected = currentRenderer;
            rendererCombo.removeAllItems();
            for (MediaRendererProxy r : renderers) {
                rendererCombo.addItem(new RendererEntry(r.getFriendlyName(), r));
            }
            // Reselect the previously selected renderer
            if (selected != null) {
                for (int i = 0; i < rendererCombo.getItemCount(); i++) {
                    if (rendererCombo.getItemAt(i).proxy().getUdn().equals(selected.getUdn())) {
                        rendererCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
        });
    }

    /**
     * Plays the given content item on the currently selected renderer.
     *
     * @param item the content item to play
     * @since 0.1.0
     */
    public void playItem(ContentItem item) {
        var renderer = currentRenderer;
        if (renderer == null || item == null) return;
        Thread.startVirtualThread(() -> {
            try {
                renderer.playItem(item);
                if (localPlaybackEngine != null) {
                    localPlaybackEngine.play(item);
                }
                if (item.getDuration() != null) {
                    trackDuration = item.getDuration();
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Playback error: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.1.0
     */
    @Override
    public void onPlaybackEvent(PlaybackEvent event) {
        SwingUtilities.invokeLater(() -> {
            switch (event) {
                case PlaybackEvent.PlayStarted s -> {
                    currentState = TransportState.PLAYING;
                    positionSlider.setEnabled(true);
                }
                case PlaybackEvent.PlayPaused p -> currentState = TransportState.PAUSED_PLAYBACK;
                case PlaybackEvent.PlayStopped s -> {
                    currentState = TransportState.STOPPED;
                    positionSlider.setValue(0);
                    positionSlider.setEnabled(false);
                }
                case PlaybackEvent.PlayCompleted c -> {
                    currentState = TransportState.STOPPED;
                    positionSlider.setValue(1000);
                    positionSlider.setEnabled(false);
                }
                case PlaybackEvent.PositionChanged pc -> {
                    trackDuration = pc.duration();
                    if (!sliderDragging && trackDuration.toMillis() > 0) {
                        int value = (int) (1000.0 * pc.position().toMillis() / trackDuration.toMillis());
                        positionSlider.setValue(Math.min(value, 1000));
                    }
                }
                case PlaybackEvent.VolumeChanged vc -> {
                    if (!volumeDragging) {
                        volumeSlider.setValue(vc.volume());
                        volumeValueLabel.setText(String.valueOf(vc.volume()));
                        muteButton.setText(vc.muted() ? MUTE_SYMBOL : UNMUTE_SYMBOL);
                    }
                }
            }
            updateButtonStates();
        });
    }

    /**
     * Sets the now-playing panel to update when items are dropped on the control panel.
     *
     * @param panel the now-playing panel
     * @since 0.1.0
     */
    public void setNowPlayingPanel(NowPlayingPanel panel) {
        this.nowPlayingPanel = panel;
    }

    /**
     * Sets the local playback engine for controlling audio playback directly.
     *
     * <p>When set, transport commands (play, pause, stop, seek) and volume
     * commands will also be forwarded to the local engine in addition to
     * the renderer proxy. This allows the control panel to control audio
     * that is playing locally through the demo renderer.
     *
     * @param engine the local playback engine
     * @since 0.1.0
     */
    public void setLocalPlaybackEngine(LocalPlaybackEngine engine) {
        this.localPlaybackEngine = engine;
    }

    // ---------------------------------------------------------------- private

    private void togglePlayPause() {
        var renderer = currentRenderer;
        if (renderer == null) return;
        Thread.startVirtualThread(() -> {
            try {
                if (currentState == TransportState.PLAYING) {
                    renderer.pause();
                    if (localPlaybackEngine != null && localPlaybackEngine.isPlaying()) {
                        localPlaybackEngine.pause();
                    }
                } else {
                    renderer.play();
                    if (localPlaybackEngine != null && !localPlaybackEngine.isPlaying()) {
                        localPlaybackEngine.resume();
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    private void toggleMute() {
        var renderer = currentRenderer;
        if (renderer == null) return;
        Thread.startVirtualThread(() -> {
            try {
                boolean muted = renderer.getMute();
                renderer.setMute(!muted);
                if (localPlaybackEngine != null) {
                    localPlaybackEngine.setMute(!muted);
                }
                SwingUtilities.invokeLater(() ->
                        muteButton.setText(muted ? UNMUTE_SYMBOL : MUTE_SYMBOL));
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    private void seekRelative(int seconds) {
        var renderer = currentRenderer;
        if (renderer == null) return;
        Thread.startVirtualThread(() -> {
            try {
                var pos = renderer.getPosition();
                Duration newPos = pos.relTime().plusSeconds(seconds);
                if (newPos.isNegative()) newPos = Duration.ZERO;
                renderer.seek(newPos);
                if (localPlaybackEngine != null) {
                    localPlaybackEngine.seek(newPos);
                }
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    private void doAction(Consumer<MediaRendererProxy> action) {
        var renderer = currentRenderer;
        if (renderer == null) return;
        Thread.startVirtualThread(() -> {
            try {
                action.accept(renderer);
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    /**
     * Executes a renderer action and also stops the local playback engine.
     */
    private void doStopAction() {
        var renderer = currentRenderer;
        if (renderer == null) return;
        Thread.startVirtualThread(() -> {
            try {
                renderer.stop();
                if (localPlaybackEngine != null) {
                    localPlaybackEngine.stop();
                }
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    private void onPositionSliderChanged(ChangeEvent e) {
        if (positionSlider.getValueIsAdjusting()) {
            sliderDragging = true;
        } else if (sliderDragging) {
            sliderDragging = false;
            if (trackDuration.toMillis() > 0) {
                long targetMs = (long) (positionSlider.getValue() / 1000.0 * trackDuration.toMillis());
                Duration target = Duration.ofMillis(targetMs);
                var renderer = currentRenderer;
                if (renderer != null) {
                    Thread.startVirtualThread(() -> {
                        try {
                            renderer.seek(target);
                        } catch (Exception ex) {
                            // Ignore
                        }
                    });
                }
                if (localPlaybackEngine != null) {
                    localPlaybackEngine.seek(target);
                }
            }
        }
    }

    private void onVolumeSliderChanged(ChangeEvent e) {
        volumeValueLabel.setText(String.valueOf(volumeSlider.getValue()));
        if (volumeSlider.getValueIsAdjusting()) {
            volumeDragging = true;
        } else if (volumeDragging) {
            volumeDragging = false;
            var renderer = currentRenderer;
            int volume = volumeSlider.getValue();
            if (renderer != null) {
                Thread.startVirtualThread(() -> {
                    try {
                        renderer.setVolume(volume);
                    } catch (Exception ex) {
                        // Ignore
                    }
                });
            }
            if (localPlaybackEngine != null) {
                localPlaybackEngine.setVolume(volume);
            }
        }
    }

    private void updateButtonStates() {
        boolean hasRenderer = currentRenderer != null;
        boolean isPlaying = currentState == TransportState.PLAYING;
        boolean isPaused = currentState == TransportState.PAUSED_PLAYBACK;

        previousButton.setEnabled(hasRenderer);
        rewindButton.setEnabled(hasRenderer && (isPlaying || isPaused));
        playPauseButton.setEnabled(hasRenderer);
        playPauseButton.setText(isPlaying ? PAUSE_SYMBOL : PLAY_SYMBOL);
        stopButton.setEnabled(hasRenderer && (isPlaying || isPaused));
        forwardButton.setEnabled(hasRenderer && (isPlaying || isPaused));
        nextButton.setEnabled(hasRenderer);
        volumeSlider.setEnabled(hasRenderer);
        muteButton.setEnabled(hasRenderer);
    }

    private void setupKeyBindings() {
        var inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "playPause");
        actionMap.put("playPause", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                togglePlayPause();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "seekBack");
        actionMap.put("seekBack", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                seekRelative(-10);
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "seekForward");
        actionMap.put("seekForward", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                seekRelative(10);
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("UP"), "volumeUp");
        actionMap.put("volumeUp", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                var r = currentRenderer;
                if (r != null) {
                    int vol = Math.min(100, volumeSlider.getValue() + 5);
                    volumeSlider.setValue(vol);
                    volumeValueLabel.setText(String.valueOf(vol));
                    Thread.startVirtualThread(() -> {
                        try { r.setVolume(vol); } catch (Exception ex) { /* ignore */ }
                    });
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("DOWN"), "volumeDown");
        actionMap.put("volumeDown", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                var r = currentRenderer;
                if (r != null) {
                    int vol = Math.max(0, volumeSlider.getValue() - 5);
                    volumeSlider.setValue(vol);
                    volumeValueLabel.setText(String.valueOf(vol));
                    Thread.startVirtualThread(() -> {
                        try { r.setVolume(vol); } catch (Exception ex) { /* ignore */ }
                    });
                }
            }
        });
    }

    // --------------------------------------------------------------- inner types

    /**
     * Entry in the renderer combo box.
     *
     * @param name  the display name
     * @param proxy the renderer proxy
     * @since 0.1.0
     */
    private record RendererEntry(String name, MediaRendererProxy proxy) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Transfer handler that accepts drops of {@link ContentItemTransferable} on
     * the playback control panel, triggering playback on the selected renderer.
     *
     * @since 0.1.0
     */
    private class PlaybackControlTransferHandler extends TransferHandler {

        /**
         * Checks whether the transfer contains a content item.
         *
         * @param support the transfer support info
         * @return {@code true} if the transfer contains a content item and a renderer is selected
         * @since 0.1.0
         */
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(ContentItemTransferable.CONTENT_ITEM_FLAVOR)
                    && currentRenderer != null;
        }

        /**
         * Handles the drop by playing the content item on the selected renderer.
         *
         * @param support the transfer support info
         * @return {@code true} if the drop was successfully handled
         * @since 0.1.0
         */
        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                ContentItem item = (ContentItem) support.getTransferable()
                        .getTransferData(ContentItemTransferable.CONTENT_ITEM_FLAVOR);
                playItem(item);
                if (nowPlayingPanel != null) {
                    nowPlayingPanel.setNowPlaying(item);
                }
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                return false;
            }
        }
    }
}
