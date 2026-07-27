package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import ssg.legoflow.upnp.mediarenderer.PlaybackListener;
import ssg.legoflow.upnp.mediarenderer.PositionInfo;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import ssg.legoflow.upnp.mediaserver.ContentItem;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Right-side panel displaying the currently playing track information
 * with a dark theme matching the web variant's CSS styling.
 *
 * <p>Shows album art (or a gradient placeholder), track title, artist, album,
 * transport state with color coding, a clickable progress bar, and time labels.
 * Updates in real time from {@link PlaybackEvent}s received via
 * {@link MediaRendererProxy#subscribeTransportEvents(PlaybackListener)}
 * and from a timer-based position polling loop.
 *
 * <p>All visual styling uses {@link DarkTheme} constants and factory methods
 * to maintain consistency with the web variant's dark theme.
 *
 * @since 1.0.0
 */
public class NowPlayingPanel extends JPanel implements PlaybackListener {

    private final JPanel albumArtPanel;
    private final JLabel albumArtLabel;
    private final JLabel titleLabel;
    private final JLabel artistLabel;
    private final JLabel stateLabel;
    private final JProgressBar progressBar;
    private final JLabel positionLabel;
    private final JLabel durationLabel;
    private final Timer positionTimer;

    private volatile MediaRendererProxy currentRenderer;
    private volatile ContentItem currentItem;
    private volatile Duration currentPosition = Duration.ZERO;
    private volatile Duration currentDuration = Duration.ZERO;
    private volatile TransportState currentState = TransportState.NO_MEDIA_PRESENT;
    private Consumer<ContentItem> dropAction;
    private final Border defaultBorder;

    /**
     * Creates a new now playing panel with dark theme styling.
     *
     * <p>The panel uses {@link DarkTheme} colors throughout: {@link DarkTheme#PANEL_BG}
     * for backgrounds, {@link DarkTheme#TEXT} for primary text, {@link DarkTheme#SECONDARY_TEXT}
     * for artist labels, and {@link DarkTheme#MUTED_TEXT} for time labels. The album art
     * placeholder renders a gradient matching the web CSS
     * {@code linear-gradient(135deg, #1e3a5f 0%, #312e81 50%, #581c87 100%)}.
     *
     * @since 1.0.0
     */
    public NowPlayingPanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(DarkTheme.PANEL_BG);
        setBorder(DarkTheme.panelBorder("Now Playing"));

        // Album art placeholder with gradient background
        albumArtLabel = new JLabel("No Media", SwingConstants.CENTER);
        albumArtLabel.setForeground(DarkTheme.SECONDARY_TEXT);
        albumArtLabel.setFont(albumArtLabel.getFont().deriveFont(14f));

        albumArtPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth();
                int h = getHeight();
                // Web CSS: linear-gradient(135deg, #1e3a5f 0%, #312e81 50%, #581c87 100%)
                GradientPaint gp1 = new GradientPaint(
                        0, 0, new Color(0x1e3a5f),
                        w * 0.5f, h * 0.5f, new Color(0x312e81));
                g2.setPaint(gp1);
                g2.fillRect(0, 0, w, h);
                GradientPaint gp2 = new GradientPaint(
                        w * 0.5f, h * 0.5f, new Color(0x312e81),
                        w, h, new Color(0x581c87));
                g2.setPaint(gp2);
                g2.fillRect(w / 2, h / 2, w / 2, h / 2);
                // Blend the second half diagonally over the full area
                GradientPaint gpFull = new GradientPaint(
                        0, 0, new Color(0x1e3a5f),
                        w, h, new Color(0x581c87));
                g2.setComposite(AlphaComposite.SrcOver.derive(0.5f));
                g2.setPaint(gpFull);
                g2.fillRect(0, 0, w, h);
                g2.dispose();
            }
        };
        albumArtPanel.setPreferredSize(new Dimension(200, 200));
        albumArtPanel.setMinimumSize(new Dimension(120, 120));
        albumArtPanel.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        albumArtPanel.setOpaque(false);
        albumArtPanel.add(albumArtLabel, BorderLayout.CENTER);

        // Track info — title: bold 15px, TEXT color
        titleLabel = new JLabel("No Track");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(DarkTheme.TEXT);

        // Artist: 12px, SECONDARY_TEXT color
        artistLabel = new JLabel(" ");
        artistLabel.setFont(artistLabel.getFont().deriveFont(12f));
        artistLabel.setForeground(DarkTheme.SECONDARY_TEXT);

        // State: bold 11px, color depends on transport state
        stateLabel = new JLabel("STOPPED");
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 11f));
        stateLabel.setForeground(DarkTheme.MUTED_TEXT);

        // Progress — dark-themed blue-fill progress bar with click-to-seek
        progressBar = DarkTheme.createProgressBar();
        progressBar.setMinimum(0);
        progressBar.setMaximum(1000);
        progressBar.setValue(0);
        progressBar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        progressBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                seekToClickPosition(e);
            }
        });

        // Time labels: 11px, MUTED_TEXT color
        positionLabel = new JLabel("0:00");
        positionLabel.setFont(positionLabel.getFont().deriveFont(11f));
        positionLabel.setForeground(DarkTheme.MUTED_TEXT);

        durationLabel = new JLabel("0:00");
        durationLabel.setFont(durationLabel.getFont().deriveFont(11f));
        durationLabel.setForeground(DarkTheme.MUTED_TEXT);

        // Layout — info panel
        var infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(DarkTheme.PANEL_BG);
        infoPanel.add(Box.createVerticalGlue());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        artistLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        stateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(titleLabel);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(artistLabel);
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(stateLabel);
        infoPanel.add(Box.createVerticalGlue());

        var topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setBackground(DarkTheme.PANEL_BG);
        topPanel.add(albumArtPanel, BorderLayout.WEST);
        topPanel.add(infoPanel, BorderLayout.CENTER);

        var progressPanel = new JPanel(new BorderLayout(6, 0));
        progressPanel.setBackground(DarkTheme.PANEL_BG);
        progressPanel.add(positionLabel, BorderLayout.WEST);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(durationLabel, BorderLayout.EAST);

        add(topPanel, BorderLayout.CENTER);
        add(progressPanel, BorderLayout.SOUTH);

        // Position polling timer (500ms while playing)
        positionTimer = new Timer(500, e -> pollPosition());
        positionTimer.setRepeats(true);

        // Drag-and-drop support
        defaultBorder = getBorder();
        setTransferHandler(new NowPlayingTransferHandler());
    }

    /**
     * Sets the media renderer to monitor for playback events.
     *
     * @param renderer the renderer proxy, or {@code null} to clear
     * @since 1.0.0
     */
    public void setRenderer(MediaRendererProxy renderer) {
        this.currentRenderer = renderer;
        if (renderer != null) {
            renderer.subscribeTransportEvents(this);
            pollPosition();
        } else {
            positionTimer.stop();
            resetDisplay();
        }
    }

    /**
     * Updates the display with information from a content item about to play.
     *
     * @param item the content item
     * @since 1.0.0
     */
    public void setNowPlaying(ContentItem item) {
        this.currentItem = item;
        SwingUtilities.invokeLater(() -> {
            if (item != null) {
                titleLabel.setText(item.getTitle());
                artistLabel.setText(item.getCreator() != null ? item.getCreator() : " ");
                if (item.getAlbumArtUri() != null) {
                    albumArtLabel.setText("[Art]");
                } else {
                    albumArtLabel.setText(typeSymbol(item.getType()));
                }
                if (item.getDuration() != null) {
                    currentDuration = item.getDuration();
                    durationLabel.setText(formatTime(currentDuration));
                }
            } else {
                resetDisplay();
            }
        });
    }

    /**
     * Returns the current transport state shown on the panel.
     *
     * @return the transport state
     * @since 1.0.0
     */
    public TransportState getCurrentState() {
        return currentState;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0.0
     */
    @Override
    public void onPlaybackEvent(PlaybackEvent event) {
        SwingUtilities.invokeLater(() -> {
            switch (event) {
                case PlaybackEvent.PlayStarted started -> {
                    currentState = TransportState.PLAYING;
                    updateStateLabel();
                    positionTimer.start();
                }
                case PlaybackEvent.PlayPaused paused -> {
                    currentState = TransportState.PAUSED_PLAYBACK;
                    currentPosition = paused.position();
                    updateStateLabel();
                    updateProgress();
                    positionTimer.stop();
                }
                case PlaybackEvent.PlayStopped stopped -> {
                    currentState = TransportState.STOPPED;
                    currentPosition = Duration.ZERO;
                    updateStateLabel();
                    updateProgress();
                    positionTimer.stop();
                }
                case PlaybackEvent.PlayCompleted completed -> {
                    currentState = TransportState.STOPPED;
                    currentPosition = currentDuration;
                    updateStateLabel();
                    updateProgress();
                    positionTimer.stop();
                }
                case PlaybackEvent.PositionChanged posChanged -> {
                    currentPosition = posChanged.position();
                    currentDuration = posChanged.duration();
                    updateProgress();
                }
                case PlaybackEvent.VolumeChanged volumeChanged -> {
                    // Volume handled by PlaybackControlPanel
                }
            }
        });
    }

    private void pollPosition() {
        var renderer = currentRenderer;
        if (renderer == null) return;

        Thread.startVirtualThread(() -> {
            try {
                PositionInfo info = renderer.getPosition();
                TransportState state = renderer.getTransportState();
                SwingUtilities.invokeLater(() -> {
                    currentPosition = info.relTime();
                    currentDuration = info.trackDuration();
                    currentState = state;
                    updateProgress();
                    updateStateLabel();

                    if (state == TransportState.PLAYING && !positionTimer.isRunning()) {
                        positionTimer.start();
                    } else if (state != TransportState.PLAYING && positionTimer.isRunning()) {
                        positionTimer.stop();
                    }
                });
            } catch (Exception e) {
                // Ignore polling errors
            }
        });
    }

    private void updateProgress() {
        positionLabel.setText(formatTime(currentPosition));
        durationLabel.setText(formatTime(currentDuration));
        if (currentDuration.toMillis() > 0) {
            int value = (int) (1000.0 * currentPosition.toMillis() / currentDuration.toMillis());
            progressBar.setValue(Math.min(value, 1000));
        } else {
            progressBar.setValue(0);
        }
    }

    /**
     * Updates the state label text and color based on current transport state.
     *
     * <p>Color mapping: PLAYING uses {@link DarkTheme#SUCCESS}, PAUSED uses
     * {@link DarkTheme#WARNING}, STOPPED uses {@link DarkTheme#MUTED_TEXT},
     * TRANSITIONING uses {@link DarkTheme#WARNING}, and all other states
     * use {@link DarkTheme#SECONDARY_TEXT}.
     */
    private void updateStateLabel() {
        stateLabel.setText(currentState.value());
        stateLabel.setForeground(switch (currentState) {
            case PLAYING -> DarkTheme.SUCCESS;
            case PAUSED_PLAYBACK -> DarkTheme.WARNING;
            case STOPPED -> DarkTheme.MUTED_TEXT;
            case TRANSITIONING -> DarkTheme.WARNING;
            default -> DarkTheme.SECONDARY_TEXT;
        });
    }

    private void resetDisplay() {
        titleLabel.setText("No Track");
        artistLabel.setText(" ");
        albumArtLabel.setText("No Media");
        stateLabel.setText("STOPPED");
        stateLabel.setForeground(DarkTheme.MUTED_TEXT);
        progressBar.setValue(0);
        positionLabel.setText("0:00");
        durationLabel.setText("0:00");
        currentPosition = Duration.ZERO;
        currentDuration = Duration.ZERO;
        currentState = TransportState.NO_MEDIA_PRESENT;
    }

    /**
     * Seeks to the position corresponding to where the user clicked on the progress bar.
     *
     * @param e the mouse event from clicking the progress bar
     */
    private void seekToClickPosition(MouseEvent e) {
        var renderer = currentRenderer;
        if (renderer == null || currentDuration.toMillis() <= 0) return;

        double ratio = (double) e.getX() / progressBar.getWidth();
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        long seekMs = (long) (ratio * currentDuration.toMillis());
        Duration seekPosition = Duration.ofMillis(seekMs);

        Thread.startVirtualThread(() -> {
            try {
                renderer.seek(seekPosition);
                SwingUtilities.invokeLater(() -> {
                    currentPosition = seekPosition;
                    updateProgress();
                });
            } catch (Exception ex) {
                // Ignore seek errors
            }
        });
    }

    /**
     * Returns the currently playing content item.
     *
     * @return the current content item, or {@code null} if nothing is playing
     * @since 1.0.0
     */
    public ContentItem getCurrentItem() {
        return currentItem;
    }

    /**
     * Sets the action to invoke when a content item is dropped on this panel.
     *
     * <p>The drop action receives the dropped {@link ContentItem} and is responsible
     * for initiating playback on the appropriate renderer.
     *
     * @param action the consumer receiving the dropped content item
     * @since 1.0.0
     */
    public void setDropAction(Consumer<ContentItem> action) {
        this.dropAction = action;
    }

    /**
     * Stops the position polling timer. Call on shutdown.
     *
     * @since 1.0.0
     */
    public void stopPolling() {
        positionTimer.stop();
    }

    /**
     * Formats a {@link Duration} as a human-readable time string.
     *
     * <p>Returns {@code "h:mm:ss"} for durations of one hour or longer,
     * and {@code "m:ss"} for shorter durations.
     *
     * @param d the duration to format, or {@code null}
     * @return the formatted time string, e.g. {@code "3:45"} or {@code "1:02:30"}
     * @since 1.0.0
     */
    static String formatTime(Duration d) {
        if (d == null) return "0:00";
        long totalSec = d.getSeconds();
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private String typeSymbol(ssg.legoflow.upnp.mediaserver.ContentItemType type) {
        return switch (type) {
            case AUDIO_ITEM -> "[ Music ]";
            case VIDEO_ITEM -> "[ Video ]";
            case IMAGE_ITEM -> "[ Image ]";
            default -> "[ Media ]";
        };
    }

    /**
     * Transfer handler enabling the now-playing panel as both a drag source and drop target
     * for {@link ContentItemTransferable} objects.
     *
     * <p>As a drop target, accepts content items and delegates to the configured
     * {@link #dropAction}. As a drag source, exports the currently playing content item.
     * Provides visual feedback via a {@link DarkTheme#ACCENT}-colored border with an
     * inset glow effect during drag-over.
     *
     * @since 1.0.0
     */
    private class NowPlayingTransferHandler extends TransferHandler {

        /**
         * Drop highlight border using {@link DarkTheme#ACCENT} blue with inset glow effect.
         * Compound border: accent line border + empty border for the glow inset.
         */
        private static final Border DROP_HIGHLIGHT_BORDER =
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(DarkTheme.ACCENT, 2),
                        BorderFactory.createEmptyBorder(3, 3, 3, 3));

        /**
         * Returns {@link TransferHandler#COPY} as the supported source action.
         *
         * @param c the source component
         * @return {@code COPY}
         * @since 1.0.0
         */
        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }

        /**
         * Creates a {@link ContentItemTransferable} from the currently playing item.
         *
         * @param c the source component
         * @return a transferable with the current item, or {@code null} if nothing is playing
         * @since 1.0.0
         */
        @Override
        protected Transferable createTransferable(JComponent c) {
            ContentItem item = currentItem;
            if (item != null && item.getResourceUrl() != null) {
                return new ContentItemTransferable(item);
            }
            return null;
        }

        /**
         * Checks whether the transfer contains a {@link ContentItemTransferable} and
         * shows a drop highlight border with {@link DarkTheme#ACCENT} glow when
         * dragging over this panel.
         *
         * @param support the transfer support info
         * @return {@code true} if the drop can be accepted
         * @since 1.0.0
         */
        @Override
        public boolean canImport(TransferSupport support) {
            boolean canAccept = support.isDataFlavorSupported(ContentItemTransferable.CONTENT_ITEM_FLAVOR);
            if (canAccept && support.isDrop()) {
                NowPlayingPanel.this.setBorder(BorderFactory.createCompoundBorder(
                        DROP_HIGHLIGHT_BORDER,
                        DarkTheme.panelBorder("Now Playing - Drop to Play")));
            }
            return canAccept;
        }

        /**
         * Handles the drop by extracting the {@link ContentItem} and invoking the
         * configured drop action on a virtual thread to avoid blocking the EDT.
         *
         * @param support the transfer support info
         * @return {@code true} if the drop was successfully handled
         * @since 1.0.0
         */
        @Override
        public boolean importData(TransferSupport support) {
            NowPlayingPanel.this.setBorder(defaultBorder);
            if (!canImport(support)) {
                return false;
            }
            NowPlayingPanel.this.setBorder(defaultBorder);
            try {
                ContentItem item = (ContentItem) support.getTransferable()
                        .getTransferData(ContentItemTransferable.CONTENT_ITEM_FLAVOR);
                if (dropAction != null) {
                    Thread.startVirtualThread(() -> dropAction.accept(item));
                }
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                return false;
            }
        }

        /**
         * Restores the default border after an export (drag) operation completes.
         *
         * @param source the source component
         * @param data   the transferable data
         * @param action the action performed
         * @since 1.0.0
         */
        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            NowPlayingPanel.this.setBorder(defaultBorder);
        }
    }
}
