package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.mediarenderer.PlaybackEvent;
import ssg.legoflow.upnp.mediarenderer.PlaybackListener;
import ssg.legoflow.upnp.mediarenderer.PositionInfo;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Local media playback engine for a UPnP/DLNA control point GUI.
 *
 * <p>Supports three media categories:
 * <ul>
 *   <li><strong>Audio</strong> — plays WAV/AIFF via {@code javax.sound.sampled};
 *       for unsupported audio formats, displays metadata with a styled message.</li>
 *   <li><strong>Images</strong> — loads JPEG/PNG/GIF/BMP from URL using
 *       {@link ImageIO#read(URL)}, scales to fit the panel while preserving
 *       aspect ratio, and displays centered on a dark background.</li>
 *   <li><strong>Video</strong> — displays metadata (title, duration, format)
 *       with a large type icon; no actual video decoding is performed.</li>
 * </ul>
 *
 * <p>All panels use the dark theme colours from {@link DarkTheme}. Implements
 * {@link PlaybackListener} to synchronize with a remote renderer, detecting
 * drift greater than 500ms and adjusting position.
 *
 * @since 0.1.0
 */
public class LocalPlaybackEngine implements PlaybackListener {

    private static final long DRIFT_THRESHOLD_MS = 500;

    /**
     * Explicitly registers third-party audio SPI providers that JDK 25's module
     * system may not auto-discover from META-INF/services in classpath jars.
     * Without this, only WAV/AIFF/AU are supported.
     */
    private static final boolean SPI_REGISTERED = registerAudioSpi();

    private static boolean registerAudioSpi() {
        // MP3 SPI (from mp3spi jar — javazoom)
        tryRegister("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader",
                javax.sound.sampled.spi.AudioFileReader.class);
        tryRegister("javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider",
                javax.sound.sampled.spi.FormatConversionProvider.class);
        // FLAC SPI (from jflac-codec jar)
        tryRegister("org.jflac.sound.spi.FlacAudioFileReader",
                javax.sound.sampled.spi.AudioFileReader.class);
        tryRegister("org.jflac.sound.spi.FlacFormatConversionProvider",
                javax.sound.sampled.spi.FormatConversionProvider.class);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> void tryRegister(String className, Class<T> spiClass) {
        try {
            var clazz = Class.forName(className);
            var instance = (T) clazz.getDeclaredConstructor().newInstance();
            // AudioSystem uses ServiceLoader internally; we can force-feed providers
            // via the undocumented but stable AudioSystem approach of just having them
            // on the classpath. Since that fails on JDK 25, we use direct instantiation
            // and invoke via the stream API when playing.
        } catch (Exception ignored) {
            // Library not on classpath — that's OK, format just won't be supported
        }
    }

    /** Cached MP3 AudioFileReader instance, or null if mp3spi not available. */
    private static final javax.sound.sampled.spi.AudioFileReader MP3_READER = createReader(
            "javazoom.spi.mpeg.sampled.file.MpegAudioFileReader");
    /** Cached MP3 FormatConversionProvider instance, or null. */
    private static final javax.sound.sampled.spi.FormatConversionProvider MP3_CONVERTER = createConverter(
            "javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider");
    /** Cached FLAC AudioFileReader instance, or null if jflac not available. */
    private static final javax.sound.sampled.spi.AudioFileReader FLAC_READER = createReader(
            "org.jflac.sound.spi.FlacAudioFileReader");
    /** Cached FLAC FormatConversionProvider instance, or null. */
    private static final javax.sound.sampled.spi.FormatConversionProvider FLAC_CONVERTER = createConverter(
            "org.jflac.sound.spi.FlacFormatConversionProvider");

    private static javax.sound.sampled.spi.AudioFileReader createReader(String className) {
        try {
            return (javax.sound.sampled.spi.AudioFileReader)
                    Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private static javax.sound.sampled.spi.FormatConversionProvider createConverter(String className) {
        try {
            return (javax.sound.sampled.spi.FormatConversionProvider)
                    Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private final AtomicReference<Clip> currentClip = new AtomicReference<>();
    private final AtomicReference<URL> currentUrl = new AtomicReference<>();
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicLong startTimeMs = new AtomicLong(0);
    private final AtomicLong pausePositionMs = new AtomicLong(0);
    private final AtomicReference<String> currentTitle = new AtomicReference<>("No Media");
    private final AtomicReference<ContentItem> currentItem = new AtomicReference<>();
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();
    private Consumer<ContentItem> dropAction;
    private Consumer<String> mediaSupportInfoListener;

    /** Current volume level (0-100). */
    private final AtomicInteger volumePercent = new AtomicInteger(75);
    /** Whether audio is muted. */
    private final AtomicBoolean muted = new AtomicBoolean(false);

    private final JPanel videoPanel;
    private final javax.swing.border.Border defaultVideoPanelBorder;

    /**
     * Creates a new local playback engine.
     *
     * <p>Initialises the video panel with dark theme colours and a
     * "No Media" placeholder. Installs a {@link LocalPlayerTransferHandler}
     * to support drag-and-drop of content items.
     *
     * @since 0.1.0
     */
    public LocalPlaybackEngine() {
        videoPanel = new JPanel(new BorderLayout());
        videoPanel.setBackground(DarkTheme.BODY_BG);
        showNoMediaPlaceholder();
        defaultVideoPanelBorder = videoPanel.getBorder();
        videoPanel.setTransferHandler(new LocalPlayerTransferHandler());
    }

    /**
     * Returns the video display panel used for metadata, images, and placeholders.
     *
     * @return the video panel
     * @since 0.1.0
     */
    public JPanel getVideoPanel() {
        return videoPanel;
    }

    /**
     * Adds a playback listener.
     *
     * @param listener the listener to add
     * @since 0.1.0
     */
    public void addPlaybackListener(PlaybackListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a playback listener.
     *
     * @param listener the listener to remove
     * @since 0.1.0
     */
    public void removePlaybackListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    /**
     * Starts playback of the given URL.
     *
     * <p>Determines the media type from the current {@link ContentItem} (if set)
     * and dispatches accordingly:
     * <ul>
     *   <li><strong>Image</strong> — loads and displays the image scaled to fit.</li>
     *   <li><strong>Audio</strong> — attempts {@code javax.sound.sampled} playback;
     *       falls back to metadata display on unsupported formats.</li>
     *   <li><strong>Video / other</strong> — displays metadata with a type icon.</li>
     * </ul>
     *
     * @param url the media URL to play
     * @since 0.1.0
     */
    public void play(URL url) {
        stop();
        currentUrl.set(url);

        ContentItem item = currentItem.get();
        ContentItemType type = item != null ? item.getType() : null;

        // Image handling — load and display the actual image
        if (type == ContentItemType.IMAGE_ITEM) {
            Thread.startVirtualThread(() -> {
                try {
                    BufferedImage image = ImageIO.read(url);
                    if (image != null) {
                        SwingUtilities.invokeLater(() -> showImage(image));
                    } else {
                        String info = "Image via javax.imageio (JDK built-in)";
                        SwingUtilities.invokeLater(() -> showMetadataPanel(item, info));
                    }
                } catch (IOException e) {
                    String info = "Image loading failed: " + e.getMessage();
                    SwingUtilities.invokeLater(() -> showMetadataPanel(item, info));
                }
                playing.set(true);
                startTimeMs.set(System.currentTimeMillis());
                fireEvent(new PlaybackEvent.PlayStarted(url.toString(), ""));
                fireMediaSupportInfo(item);
            });
            return;
        }

        // Video and non-audio types — show metadata only
        if (type == ContentItemType.VIDEO_ITEM) {
            String videoInfo = "Video playback not supported (no video decoder library)";
            SwingUtilities.invokeLater(() -> showMetadataPanel(item, videoInfo));
            playing.set(true);
            startTimeMs.set(System.currentTimeMillis());
            fireEvent(new PlaybackEvent.PlayStarted(url.toString(), ""));
            fireMediaSupportInfo(item);
            return;
        }

        // Audio (and generic) — try javax.sound.sampled with explicit SPI fallback
        Thread.startVirtualThread(() -> {
            try {
                AudioInputStream audioStream = getAudioInputStream(url);
                AudioFormat format = audioStream.getFormat();

                // Convert to PCM if needed (use explicit converter if SPI fails)
                if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                    AudioFormat targetFormat = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            format.getSampleRate(), 16,
                            format.getChannels(),
                            format.getChannels() * 2,
                            format.getSampleRate(), false);
                    audioStream = convertAudioStream(targetFormat, audioStream);
                }

                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                currentClip.set(clip);
                applyVolumeToClip();
                clip.start();
                playing.set(true);
                startTimeMs.set(System.currentTimeMillis());
                pausePositionMs.set(0);

                String supportInfo = describeAudioSupport(url);
                SwingUtilities.invokeLater(() -> showMetadataPanel(item, supportInfo));
                fireEvent(new PlaybackEvent.PlayStarted(url.toString(), ""));
                fireMediaSupportInfo(item);

                // Monitor completion
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP && playing.get()) {
                        if (clip.getMicrosecondPosition() >= clip.getMicrosecondLength()) {
                            playing.set(false);
                            fireEvent(new PlaybackEvent.PlayCompleted());
                        }
                    }
                });
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                // Unsupported audio format — show metadata with message
                System.err.println("[LocalPlaybackEngine] Audio playback failed for " + url
                        + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace(System.err);
                String supportInfo = describeUnsupportedAudio(url);
                SwingUtilities.invokeLater(() -> showUnsupportedAudioPanel(item, supportInfo));
                playing.set(true);
                startTimeMs.set(System.currentTimeMillis());
                fireEvent(new PlaybackEvent.PlayStarted(url.toString(), ""));
                fireMediaSupportInfo(item);
            }
        });
    }

    /**
     * Starts playback of a content item, using its resource URL and title.
     *
     * @param item the content item to play
     * @since 0.1.0
     */
    public void play(ContentItem item) {
        currentTitle.set(item.getTitle());
        currentItem.set(item);
        if (item.getResourceUrl() != null) {
            play(item.getResourceUrl());
        }
    }

    /**
     * Pauses playback.
     *
     * @since 0.1.0
     */
    public void pause() {
        Clip clip = currentClip.get();
        if (clip != null && clip.isRunning()) {
            pausePositionMs.set(clip.getMicrosecondPosition() / 1000);
            clip.stop();
        } else {
            pausePositionMs.set(getPosition().toMillis());
        }
        playing.set(false);
        fireEvent(new PlaybackEvent.PlayPaused(getPosition()));
    }

    /**
     * Resumes playback from the current position.
     *
     * @since 0.1.0
     */
    public void resume() {
        Clip clip = currentClip.get();
        if (clip != null) {
            clip.start();
        }
        playing.set(true);
        startTimeMs.set(System.currentTimeMillis() - pausePositionMs.get());
        fireEvent(new PlaybackEvent.PlayStarted(
                currentUrl.get() != null ? currentUrl.get().toString() : "", ""));
    }

    /**
     * Stops playback and releases resources.
     *
     * @since 0.1.0
     */
    public void stop() {
        playing.set(false);
        Clip clip = currentClip.getAndSet(null);
        if (clip != null) {
            clip.stop();
            clip.close();
        }
        pausePositionMs.set(0);
        startTimeMs.set(0);
        fireEvent(new PlaybackEvent.PlayStopped());
    }

    /**
     * Seeks to the specified position.
     *
     * @param position the target position
     * @since 0.1.0
     */
    public void seek(Duration position) {
        Clip clip = currentClip.get();
        if (clip != null) {
            long micros = position.toMillis() * 1000;
            if (micros >= 0 && micros < clip.getMicrosecondLength()) {
                clip.setMicrosecondPosition(micros);
            }
        }
        pausePositionMs.set(position.toMillis());
        startTimeMs.set(System.currentTimeMillis() - position.toMillis());
    }

    /**
     * Returns the current playback position.
     *
     * @return the current position
     * @since 0.1.0
     */
    public Duration getPosition() {
        Clip clip = currentClip.get();
        if (clip != null) {
            return Duration.ofMillis(clip.getMicrosecondPosition() / 1000);
        }
        if (playing.get()) {
            return Duration.ofMillis(System.currentTimeMillis() - startTimeMs.get());
        }
        return Duration.ofMillis(pausePositionMs.get());
    }

    /**
     * Returns whether playback is currently active.
     *
     * @return {@code true} if playing
     * @since 0.1.0
     */
    public boolean isPlaying() {
        return playing.get();
    }

    /**
     * Sets the volume level (0-100) and applies it to the current audio clip.
     *
     * @param volume the desired volume (0-100)
     * @since 0.1.0
     */
    public void setVolume(int volume) {
        volumePercent.set(Math.max(0, Math.min(100, volume)));
        applyVolumeToClip();
    }

    /**
     * Returns the current volume level.
     *
     * @return the volume (0-100)
     * @since 0.1.0
     */
    public int getVolume() {
        return volumePercent.get();
    }

    /**
     * Sets the mute state and applies it to the current audio clip.
     *
     * @param mute {@code true} to mute, {@code false} to unmute
     * @since 0.1.0
     */
    public void setMute(boolean mute) {
        muted.set(mute);
        applyVolumeToClip();
    }

    /**
     * Returns whether audio is currently muted.
     *
     * @return {@code true} if muted
     * @since 0.1.0
     */
    public boolean isMuted() {
        return muted.get();
    }

    /**
     * Applies the current volume and mute settings to the active audio clip.
     */
    private void applyVolumeToClip() {
        Clip clip = currentClip.get();
        if (clip == null || !clip.isOpen()) return;
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                if (muted.get()) {
                    gainControl.setValue(gainControl.getMinimum());
                } else {
                    // Convert 0-100 linear volume to decibels
                    float vol = volumePercent.get() / 100.0f;
                    if (vol <= 0) {
                        gainControl.setValue(gainControl.getMinimum());
                    } else {
                        float dB = (float) (20.0 * Math.log10(vol));
                        dB = Math.max(dB, gainControl.getMinimum());
                        dB = Math.min(dB, gainControl.getMaximum());
                        gainControl.setValue(dB);
                    }
                }
            }
        } catch (Exception e) {
            // Not all clips support volume control
        }
    }

    /**
     * Receives playback events from a remote renderer and synchronizes local position.
     *
     * <p>If the drift between local and remote position exceeds 500ms,
     * the local position is adjusted to match the remote.
     *
     * {@inheritDoc}
     *
     * @since 0.1.0
     */
    @Override
    public void onPlaybackEvent(PlaybackEvent event) {
        if (event instanceof PlaybackEvent.PositionChanged pc) {
            synchronizeWithRemote(pc.position());
        }
    }

    /**
     * Compares local position with the remote position and adjusts if drift exceeds threshold.
     *
     * @param remotePosition the remote renderer's current position
     * @since 0.1.0
     */
    public void synchronizeWithRemote(Duration remotePosition) {
        Duration localPos = getPosition();
        long driftMs = Math.abs(localPos.toMillis() - remotePosition.toMillis());
        if (driftMs > DRIFT_THRESHOLD_MS) {
            seek(remotePosition);
        }
    }

    /**
     * Returns the drift threshold in milliseconds.
     *
     * @return the threshold (500ms)
     * @since 0.1.0
     */
    public static long getDriftThresholdMs() {
        return DRIFT_THRESHOLD_MS;
    }

    /**
     * Returns the currently playing content item.
     *
     * @return the current content item, or {@code null} if nothing is playing
     * @since 0.1.0
     */
    public ContentItem getCurrentItem() {
        return currentItem.get();
    }

    /**
     * Sets the action to invoke when a content item is dropped on the video panel.
     *
     * <p>The drop action receives the dropped {@link ContentItem} and is responsible
     * for initiating local playback.
     *
     * @param action the consumer receiving the dropped content item
     * @since 0.1.0
     */
    public void setDropAction(Consumer<ContentItem> action) {
        this.dropAction = action;
    }

    /**
     * Sets a listener that receives media support info strings when media is played.
     *
     * <p>The listener receives a human-readable description of which library/framework
     * provides support for the current media type, or a "not supported" message.
     *
     * @param listener the consumer receiving the support info string
     * @since 0.1.0
     */
    public void setMediaSupportInfoListener(Consumer<String> listener) {
        this.mediaSupportInfoListener = listener;
    }

    /**
     * Fires the media support info to the registered listener.
     */
    private void fireMediaSupportInfo(ContentItem item) {
        var listener = mediaSupportInfoListener;
        if (listener != null && item != null) {
            String info = describeMediaSupport(item);
            if (info != null && !info.isEmpty()) {
                listener.accept(info);
            }
        }
    }

    // ------------------------------------------------------------------ UI helpers

    /**
     * Shows the "No Media" placeholder on the video panel.
     */
    private void showNoMediaPlaceholder() {
        videoPanel.removeAll();
        videoPanel.setLayout(new GridBagLayout());

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        JLabel icon = new JLabel("🎵", SwingConstants.CENTER); // musical note
        icon.setFont(new Font(Font.DIALOG, Font.PLAIN, 48));
        icon.setForeground(DarkTheme.MUTED_TEXT);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(icon);
        center.add(Box.createVerticalStrut(12));

        JLabel text = new JLabel("No Media");
        text.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        text.setForeground(DarkTheme.SECONDARY_TEXT);
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(text);

        videoPanel.add(center);
        videoPanel.revalidate();
        videoPanel.repaint();
    }

    /**
     * Shows a metadata panel for the given content item with type icon, title,
     * artist/creator, duration, and format information.
     *
     * @param item the content item (may be {@code null})
     */
    private void showMetadataPanel(ContentItem item) {
        showMetadataPanel(item, null);
    }

    private void showMetadataPanel(ContentItem item, String supportInfo) {
        videoPanel.removeAll();
        videoPanel.setLayout(new GridBagLayout());

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        // Type icon
        String emoji = resolveTypeEmoji(item);
        JLabel iconLabel = new JLabel(emoji, SwingConstants.CENTER);
        iconLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 64));
        iconLabel.setForeground(DarkTheme.TEXT);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(iconLabel);
        center.add(Box.createVerticalStrut(16));

        // Title
        String title = item != null ? item.getTitle() : currentTitle.get();
        JLabel titleLabel = new JLabel(title != null ? title : "Unknown");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        titleLabel.setForeground(DarkTheme.TEXT);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(titleLabel);
        center.add(Box.createVerticalStrut(6));

        // Artist / creator
        if (item != null && item.getCreator() != null && !item.getCreator().isBlank()) {
            JLabel artistLabel = new JLabel(item.getCreator());
            artistLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            artistLabel.setForeground(DarkTheme.SECONDARY_TEXT);
            artistLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(artistLabel);
            center.add(Box.createVerticalStrut(12));
        }

        // Duration
        if (item != null && item.getDuration() != null) {
            JLabel durationLabel = new JLabel(formatDuration(item.getDuration()));
            durationLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            durationLabel.setForeground(DarkTheme.MUTED_TEXT);
            durationLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(durationLabel);
            center.add(Box.createVerticalStrut(4));
        }

        // Format / MIME type
        String format = resolveFormat(item);
        if (format != null) {
            JLabel formatLabel = new JLabel(format);
            formatLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            formatLabel.setForeground(DarkTheme.MUTED_TEXT);
            formatLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(formatLabel);
        }

        // Support info
        if (supportInfo != null && !supportInfo.isEmpty()) {
            center.add(Box.createVerticalStrut(8));
            JLabel supportLabel = new JLabel(supportInfo);
            supportLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
            supportLabel.setForeground(DarkTheme.ACCENT);
            supportLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(supportLabel);
        }

        videoPanel.add(center);
        videoPanel.revalidate();
        videoPanel.repaint();
    }

    /**
     * Shows metadata for an unsupported audio format, with an additional message
     * indicating that local playback is not available.
     *
     * @param item the content item (may be {@code null})
     */
    private void showUnsupportedAudioPanel(ContentItem item, String supportInfo) {
        videoPanel.removeAll();
        videoPanel.setLayout(new GridBagLayout());

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        // Type icon
        JLabel iconLabel = new JLabel("🎵", SwingConstants.CENTER);
        iconLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 64));
        iconLabel.setForeground(DarkTheme.TEXT);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(iconLabel);
        center.add(Box.createVerticalStrut(16));

        // Title
        String title = item != null ? item.getTitle() : currentTitle.get();
        JLabel titleLabel = new JLabel(title != null ? title : "Unknown");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        titleLabel.setForeground(DarkTheme.TEXT);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(titleLabel);
        center.add(Box.createVerticalStrut(6));

        // Artist / creator
        if (item != null && item.getCreator() != null && !item.getCreator().isBlank()) {
            JLabel artistLabel = new JLabel(item.getCreator());
            artistLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            artistLabel.setForeground(DarkTheme.SECONDARY_TEXT);
            artistLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(artistLabel);
            center.add(Box.createVerticalStrut(12));
        }

        // Duration
        if (item != null && item.getDuration() != null) {
            JLabel durationLabel = new JLabel(formatDuration(item.getDuration()));
            durationLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            durationLabel.setForeground(DarkTheme.MUTED_TEXT);
            durationLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(durationLabel);
            center.add(Box.createVerticalStrut(4));
        }

        // Format
        String format = resolveFormat(item);
        if (format != null) {
            JLabel formatLabel = new JLabel(format);
            formatLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            formatLabel.setForeground(DarkTheme.MUTED_TEXT);
            formatLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(formatLabel);
            center.add(Box.createVerticalStrut(12));
        }

        // Unsupported format message
        String message = (supportInfo != null && !supportInfo.isEmpty())
                ? supportInfo
                : "Audio format not supported for local playback";
        JLabel msgLabel = new JLabel(message);
        msgLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
        msgLabel.setForeground(DarkTheme.WARNING);
        msgLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(msgLabel);

        videoPanel.add(center);
        videoPanel.revalidate();
        videoPanel.repaint();
    }

    /**
     * Displays an image scaled to fit the video panel while maintaining aspect ratio,
     * centered on a dark background.
     *
     * @param image the image to display
     */
    private void showImage(BufferedImage image) {
        videoPanel.removeAll();
        videoPanel.setLayout(new BorderLayout());

        JPanel imagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (image == null) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);

                int panelW = getWidth();
                int panelH = getHeight();
                int imgW = image.getWidth();
                int imgH = image.getHeight();

                // Scale to fit while maintaining aspect ratio
                double scaleX = (double) panelW / imgW;
                double scaleY = (double) panelH / imgH;
                double scale = Math.min(scaleX, scaleY);

                int scaledW = (int) (imgW * scale);
                int scaledH = (int) (imgH * scale);

                // Center on the panel
                int x = (panelW - scaledW) / 2;
                int y = (panelH - scaledH) / 2;

                g2.drawImage(image, x, y, scaledW, scaledH, null);
                g2.dispose();
            }
        };
        imagePanel.setBackground(DarkTheme.BODY_BG);

        videoPanel.add(imagePanel, BorderLayout.CENTER);
        videoPanel.revalidate();
        videoPanel.repaint();
    }

    /**
     * Resolves the type emoji for a content item based on its {@link ContentItemType}.
     *
     * @param item the content item (may be {@code null})
     * @return the emoji string for the type
     */
    private static String resolveTypeEmoji(ContentItem item) {
        if (item == null) {
            return "🎵"; // musical note
        }
        return switch (item.getType()) {
            case AUDIO_ITEM -> "🎵";    // musical note
            case VIDEO_ITEM -> "🎬";    // clapper board
            case IMAGE_ITEM -> "🖼";    // framed picture
            default -> "📄";             // page facing up
        };
    }

    /**
     * Resolves the format string from a content item's protocol info or resource URL.
     *
     * @param item the content item (may be {@code null})
     * @return the format description, or {@code null} if unavailable
     */
    private static String resolveFormat(ContentItem item) {
        if (item == null) {
            return null;
        }
        if (item.getProtocolInfo() != null
                && item.getProtocolInfo().contentFormat() != null
                && !item.getProtocolInfo().contentFormat().equals("*")) {
            return item.getProtocolInfo().contentFormat();
        }
        // Attempt to infer from the resource URL file extension
        URL url = item.getResourceUrl();
        if (url != null) {
            String path = url.getPath();
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                return path.substring(dot + 1).toUpperCase();
            }
        }
        return null;
    }

    /**
     * Formats a {@link Duration} as {@code H:MM:SS} or {@code M:SS}.
     *
     * @param duration the duration to format
     * @return the formatted string
     */
    private static String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    // ------------------------------------------------------------------ audio SPI helpers

    /**
     * Opens a buffered input stream from a media URL, sending DLNA transfer-mode
     * headers so that DLNA servers respond correctly.
     */
    private static BufferedInputStream openMediaStream(URL url) throws IOException {
        if ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("transferMode.dlna.org", "Streaming");
            conn.setRequestProperty("User-Agent", "LegoFlow-MCC/1.0 DLNA");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            return new BufferedInputStream(conn.getInputStream());
        }
        return new BufferedInputStream(url.openStream());
    }

    /**
     * Opens an AudioInputStream, trying explicit SPI providers first (matched by
     * URL extension) to avoid the JDK 25 AudioSystem bug where the FLAC SPI reader
     * corrupts the stream's mark/reset state when probing non-FLAC files.
     *
     * <p>Order: extension-matched explicit provider → other explicit providers
     * → AudioSystem (for built-in WAV/AIFF/AU). Each attempt opens a fresh stream
     * to avoid mark/reset corruption across providers.
     */
    private static AudioInputStream getAudioInputStream(URL url)
            throws UnsupportedAudioFileException, IOException {

        String ext = extractExtension(url);

        // 1. Try the best-match explicit provider first (avoids stream corruption)
        if ("mp3".equals(ext) && MP3_READER != null) {
            try {
                return MP3_READER.getAudioInputStream(openMediaStream(url));
            } catch (UnsupportedAudioFileException | IOException e) {
                // fall through
            }
        }
        if ("flac".equals(ext) && FLAC_READER != null) {
            try {
                return FLAC_READER.getAudioInputStream(openMediaStream(url));
            } catch (UnsupportedAudioFileException | IOException e) {
                // fall through
            }
        }

        // 2. Try the other explicit provider (fresh stream each time)
        if (!"mp3".equals(ext) && MP3_READER != null) {
            try {
                return MP3_READER.getAudioInputStream(openMediaStream(url));
            } catch (UnsupportedAudioFileException | IOException ignored) {
                // Not MP3
            }
        }
        if (!"flac".equals(ext) && FLAC_READER != null) {
            try {
                return FLAC_READER.getAudioInputStream(openMediaStream(url));
            } catch (UnsupportedAudioFileException | IOException ignored) {
                // Not FLAC
            }
        }

        // 3. AudioSystem for built-in formats (WAV, AIFF, AU) — only if explicit
        //    providers didn't match. Uses a fresh stream with a large mark buffer
        //    to survive any remaining SPI probing.
        try {
            var stream = openMediaStream(url);
            stream.mark(256 * 1024); // 256KB mark limit for SPI probing
            return AudioSystem.getAudioInputStream(stream);
        } catch (UnsupportedAudioFileException | IOException e) {
            // fall through
        }

        throw new UnsupportedAudioFileException("No audio reader found for: " + url);
    }

    /**
     * Converts an AudioInputStream to the target format, trying the standard
     * AudioSystem first, then explicit MP3/FLAC converters.
     */
    private static AudioInputStream convertAudioStream(AudioFormat targetFormat,
                                                        AudioInputStream source)
            throws UnsupportedAudioFileException {
        // 1. Standard conversion
        try {
            if (AudioSystem.isConversionSupported(targetFormat, source.getFormat())) {
                return AudioSystem.getAudioInputStream(targetFormat, source);
            }
        } catch (Exception ignored) {
            // Fall through
        }

        // 2. Explicit MP3 converter
        if (MP3_CONVERTER != null && MP3_CONVERTER.isConversionSupported(targetFormat, source.getFormat())) {
            return MP3_CONVERTER.getAudioInputStream(targetFormat, source);
        }

        // 3. Explicit FLAC converter
        if (FLAC_CONVERTER != null && FLAC_CONVERTER.isConversionSupported(targetFormat, source.getFormat())) {
            return FLAC_CONVERTER.getAudioInputStream(targetFormat, source);
        }

        throw new UnsupportedAudioFileException(
                "Cannot convert from " + source.getFormat() + " to " + targetFormat);
    }

    // ------------------------------------------------------------------ support info

    /**
     * Describes which library provides audio support for the given URL.
     */
    private static String describeAudioSupport(URL url) {
        String ext = extractExtension(url);
        return switch (ext) {
            case "mp3" -> "MP3 via mp3spi/JLayer";
            case "flac" -> "FLAC via jflac-codec";
            case "wav", "wave" -> "WAV via javax.sound.sampled (JDK built-in)";
            case "aiff", "aif" -> "AIFF via javax.sound.sampled (JDK built-in)";
            case "au" -> "AU via javax.sound.sampled (JDK built-in)";
            default -> "Audio via javax.sound.sampled";
        };
    }

    /**
     * Describes why audio is not supported for the given URL.
     */
    private static String describeUnsupportedAudio(URL url) {
        String ext = extractExtension(url);
        return switch (ext) {
            case "mp3" -> MP3_READER != null
                    ? "MP3 reader available but decoding failed"
                    : "MP3 requires mp3spi library (not loaded)";
            case "flac" -> FLAC_READER != null
                    ? "FLAC reader available but decoding failed"
                    : "FLAC requires jflac-codec library (not loaded)";
            case "ogg" -> "OGG/Vorbis not supported (no SPI provider)";
            case "aac", "m4a" -> "AAC not supported (no SPI provider)";
            case "wma" -> "WMA not supported (no SPI provider)";
            default -> "Format not supported by available audio libraries";
        };
    }

    /**
     * Describes media support for a given content item type.
     */
    static String describeMediaSupport(ContentItem item) {
        if (item == null) return "";
        return switch (item.getType()) {
            case AUDIO_ITEM -> describeAudioSupport(item.getResourceUrl());
            case IMAGE_ITEM -> "Image via javax.imageio (JDK built-in)";
            case VIDEO_ITEM -> "Video playback not supported (no video decoder library)";
            case CONTAINER -> "";
            default -> "";
        };
    }

    /**
     * Extracts the file extension from a URL, lowercase.
     */
    private static String extractExtension(URL url) {
        if (url == null) return "";
        String path = url.getPath();
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
            String ext = path.substring(dot + 1).toLowerCase();
            // Strip query params if somehow present in path
            int q = ext.indexOf('?');
            return q >= 0 ? ext.substring(0, q) : ext;
        }
        return "";
    }

    // ------------------------------------------------------------------ event fire

    private void fireEvent(PlaybackEvent event) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onPlaybackEvent(event);
            } catch (Exception e) {
                // Swallow listener exceptions
            }
        }
    }

    // --------------------------------------------------------- transfer handler

    /**
     * Transfer handler enabling the local player video panel as both a drag source
     * and drop target for {@link ContentItemTransferable} objects.
     *
     * <p>As a drop target, accepts content items and delegates to the configured
     * {@link #dropAction}. As a drag source, exports the currently playing content item.
     * Provides visual feedback via border highlighting (using {@link DarkTheme#SUCCESS})
     * during drag-over.
     *
     * @since 0.1.0
     */
    private class LocalPlayerTransferHandler extends TransferHandler {

        private static final javax.swing.border.Border DROP_HIGHLIGHT_BORDER =
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(DarkTheme.SUCCESS, 2),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2));

        /**
         * Returns {@link TransferHandler#COPY} as the supported source action.
         *
         * @param c the source component
         * @return {@code COPY}
         * @since 0.1.0
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
         * @since 0.1.0
         */
        @Override
        protected Transferable createTransferable(JComponent c) {
            ContentItem item = currentItem.get();
            if (item != null && item.getResourceUrl() != null) {
                return new ContentItemTransferable(item);
            }
            return null;
        }

        /**
         * Checks whether the transfer contains a {@link ContentItemTransferable} and
         * shows a drop highlight border when dragging over the video panel.
         *
         * @param support the transfer support info
         * @return {@code true} if the drop can be accepted
         * @since 0.1.0
         */
        @Override
        public boolean canImport(TransferSupport support) {
            boolean canAccept = support.isDataFlavorSupported(ContentItemTransferable.CONTENT_ITEM_FLAVOR);
            if (canAccept && support.isDrop()) {
                videoPanel.setBorder(DROP_HIGHLIGHT_BORDER);
            }
            return canAccept;
        }

        /**
         * Handles the drop by extracting the {@link ContentItem} and invoking the
         * configured drop action on a virtual thread to avoid blocking the EDT.
         *
         * @param support the transfer support info
         * @return {@code true} if the drop was successfully handled
         * @since 0.1.0
         */
        @Override
        public boolean importData(TransferSupport support) {
            videoPanel.setBorder(defaultVideoPanelBorder);
            if (!canImport(support)) {
                return false;
            }
            videoPanel.setBorder(defaultVideoPanelBorder);
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
         * @since 0.1.0
         */
        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            videoPanel.setBorder(defaultVideoPanelBorder);
        }
    }
}
