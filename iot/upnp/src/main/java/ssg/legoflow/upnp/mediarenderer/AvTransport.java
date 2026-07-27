package ssg.legoflow.upnp.mediarenderer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * UPnP AVTransport:1 service implementation.
 *
 * <p>Manages media playback on a renderer device, including transport
 * controls (play, pause, stop, seek) and state tracking. Implements
 * a state machine: NO_MEDIA → STOPPED → PLAYING ↔ PAUSED.
 *
 * @since 1.0.0
 */
public class AvTransport {

    /** UPnP service type for AVTransport:1. */
    public static final String SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1";

    /** UPnP service ID for AVTransport. */
    public static final String SERVICE_ID = "urn:upnp-org:serviceId:AVTransport";

    /**
     * Seek mode for positioning within media.
     *
     * @since 1.0.0
     */
    public enum SeekMode {
        /** Seek to a track number. */
        TRACK_NR("TRACK_NR"),
        /** Seek to an absolute time position. */
        ABS_TIME("ABS_TIME"),
        /** Seek to a relative time position. */
        REL_TIME("REL_TIME"),
        /** Seek to an absolute counter position. */
        ABS_COUNT("ABS_COUNT"),
        /** Seek to a relative counter position. */
        REL_COUNT("REL_COUNT");

        private final String value;

        SeekMode(String value) {
            this.value = value;
        }

        /**
         * Returns the UPnP string value.
         *
         * @return the value string
         * @since 1.0.0
         */
        public String value() {
            return value;
        }

        /**
         * Parses a seek mode from its string value.
         *
         * @param value the string value
         * @return the seek mode
         * @since 1.0.0
         */
        public static SeekMode fromValue(String value) {
            for (SeekMode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown seek mode: " + value);
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();

    // Transport state
    private volatile TransportState transportState = TransportState.NO_MEDIA_PRESENT;
    private volatile TransportStatus transportStatus = TransportStatus.OK;
    private volatile String currentSpeed = "1";

    // Current media info
    private volatile String currentUri = "";
    private volatile String currentUriMetadata = "";
    private volatile String nextUri = "";
    private volatile String nextUriMetadata = "";
    private volatile int nrTracks = 0;
    private volatile Duration mediaDuration = Duration.ZERO;

    // Position info
    private volatile int currentTrack = 0;
    private volatile Duration trackDuration = Duration.ZERO;
    private volatile Duration relTime = Duration.ZERO;
    private volatile Duration absTime = Duration.ZERO;

    // Transport settings
    private volatile PlayMode playMode = PlayMode.NORMAL;

    /**
     * Creates a new AVTransport service.
     *
     * @since 1.0.0
     */
    public AvTransport() {
    }

    /**
     * Sets the URI of the media to be played.
     *
     * @param instanceId         the instance ID (typically 0)
     * @param currentUri         the URI of the media resource
     * @param currentUriMetadata DIDL-Lite XML metadata for the media
     * @since 1.0.0
     */
    public void setAVTransportURI(int instanceId, String currentUri, String currentUriMetadata) {
        lock.lock();
        try {
            this.currentUri = Objects.requireNonNull(currentUri, "currentUri must not be null");
            this.currentUriMetadata = currentUriMetadata != null ? currentUriMetadata : "";
            this.nrTracks = 1;
            this.currentTrack = 1;
            this.mediaDuration = Duration.ZERO;
            this.trackDuration = Duration.ZERO;
            this.relTime = Duration.ZERO;
            this.absTime = Duration.ZERO;
            this.transportState = TransportState.STOPPED;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Starts or resumes playback.
     *
     * @param instanceId the instance ID
     * @param speed      the playback speed ("1" for normal)
     * @throws IllegalStateException if no media is loaded
     * @since 1.0.0
     */
    public void play(int instanceId, String speed) {
        lock.lock();
        try {
            if (transportState == TransportState.NO_MEDIA_PRESENT) {
                throw new IllegalStateException("No media present");
            }
            this.currentSpeed = speed != null ? speed : "1";
            this.transportState = TransportState.PLAYING;
        } finally {
            lock.unlock();
        }
        fireEvent(new PlaybackEvent.PlayStarted(currentUri, currentUriMetadata));
    }

    /**
     * Pauses playback.
     *
     * @param instanceId the instance ID
     * @throws IllegalStateException if not currently playing
     * @since 1.0.0
     */
    public void pause(int instanceId) {
        lock.lock();
        try {
            if (transportState != TransportState.PLAYING) {
                throw new IllegalStateException("Not currently playing, state: " + transportState);
            }
            this.transportState = TransportState.PAUSED_PLAYBACK;
        } finally {
            lock.unlock();
        }
        fireEvent(new PlaybackEvent.PlayPaused(relTime));
    }

    /**
     * Stops playback.
     *
     * @param instanceId the instance ID
     * @since 1.0.0
     */
    public void stop(int instanceId) {
        lock.lock();
        try {
            if (transportState == TransportState.NO_MEDIA_PRESENT) {
                return;
            }
            this.transportState = TransportState.STOPPED;
            this.relTime = Duration.ZERO;
            this.absTime = Duration.ZERO;
        } finally {
            lock.unlock();
        }
        fireEvent(new PlaybackEvent.PlayStopped());
    }

    /**
     * Seeks to a position within the current media.
     *
     * @param instanceId the instance ID
     * @param mode       the seek mode
     * @param target     the seek target (time string for time modes, number for count/track modes)
     * @since 1.0.0
     */
    public void seek(int instanceId, SeekMode mode, String target) {
        lock.lock();
        try {
            if (transportState == TransportState.NO_MEDIA_PRESENT) {
                throw new IllegalStateException("No media present");
            }
            switch (mode) {
                case ABS_TIME, REL_TIME -> {
                    Duration seekPos = parseDuration(target);
                    this.relTime = seekPos;
                    this.absTime = seekPos;
                }
                case TRACK_NR -> this.currentTrack = Integer.parseInt(target);
                case ABS_COUNT -> {} // not commonly implemented
                case REL_COUNT -> {} // not commonly implemented
            }
        } finally {
            lock.unlock();
        }
        fireEvent(new PlaybackEvent.PositionChanged(relTime, trackDuration));
    }

    /**
     * Advances to the next track.
     *
     * @param instanceId the instance ID
     * @since 1.0.0
     */
    public void next(int instanceId) {
        lock.lock();
        try {
            if (currentTrack < nrTracks) {
                currentTrack++;
                relTime = Duration.ZERO;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns to the previous track.
     *
     * @param instanceId the instance ID
     * @since 1.0.0
     */
    public void previous(int instanceId) {
        lock.lock();
        try {
            if (currentTrack > 1) {
                currentTrack--;
                relTime = Duration.ZERO;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the next URI to play after the current media completes (gapless playback).
     *
     * <p>When the current track completes, if a next URI has been set, the transport
     * will automatically transition to playing the next URI without going through
     * the STOPPED state.
     *
     * @param instanceId      the instance ID (typically 0)
     * @param nextUri         the URI of the next media resource
     * @param nextUriMetadata DIDL-Lite XML metadata for the next media
     * @since 1.0.0
     */
    public void setNextAVTransportURI(int instanceId, String nextUri, String nextUriMetadata) {
        lock.lock();
        try {
            this.nextUri = Objects.requireNonNull(nextUri, "nextUri must not be null");
            this.nextUriMetadata = nextUriMetadata != null ? nextUriMetadata : "";
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the device capabilities for media playback.
     *
     * <p>Reports the supported playback and recording media types. For a network-based
     * media renderer, the primary play medium is {@code NETWORK}.
     *
     * @param instanceId the instance ID
     * @return a map containing PlayMedia, RecMedia, and RecQualityModes capabilities
     * @since 1.0.0
     */
    public DeviceCapabilities getDeviceCapabilities(int instanceId) {
        return new DeviceCapabilities("NETWORK,NONE", "NOT_IMPLEMENTED", "NOT_IMPLEMENTED");
    }

    /**
     * Returns the current transport settings (play mode, recording quality mode).
     *
     * @param instanceId the instance ID
     * @return the transport settings
     * @since 1.0.0
     */
    public TransportSettings getTransportSettings(int instanceId) {
        return new TransportSettings(playMode, "NOT_IMPLEMENTED");
    }

    /**
     * Sets the play mode for the transport.
     *
     * @param instanceId the instance ID
     * @param newMode    the play mode to set
     * @since 1.0.0
     */
    public void setPlayMode(int instanceId, PlayMode newMode) {
        Objects.requireNonNull(newMode, "newMode must not be null");
        this.playMode = newMode;
    }

    /**
     * Play mode for the AVTransport (how to handle track transitions).
     *
     * @since 1.0.0
     */
    public enum PlayMode {
        /** Normal playback order. */
        NORMAL("NORMAL"),
        /** Shuffle play (random order). */
        SHUFFLE("SHUFFLE"),
        /** Repeat the current track. */
        REPEAT_ONE("REPEAT_ONE"),
        /** Repeat all tracks. */
        REPEAT_ALL("REPEAT_ALL"),
        /** Random play (like shuffle). */
        RANDOM("RANDOM"),
        /** Direct play (one track only). */
        DIRECT_1("DIRECT_1"),
        /** Introduction mode (preview only). */
        INTRO("INTRO");

        private final String value;

        PlayMode(String value) {
            this.value = value;
        }

        /**
         * Returns the UPnP string value.
         *
         * @return the value string
         * @since 1.0.0
         */
        public String value() {
            return value;
        }

        /**
         * Parses a play mode from its string value.
         *
         * @param value the string value
         * @return the play mode
         * @since 1.0.0
         */
        public static PlayMode fromValue(String value) {
            for (PlayMode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown play mode: " + value);
        }
    }

    /**
     * Device capabilities record.
     *
     * @param playMedia    comma-separated list of supported play media
     * @param recMedia     comma-separated list of supported recording media
     * @param recQualityModes comma-separated list of supported recording quality modes
     * @since 1.0.0
     */
    public record DeviceCapabilities(String playMedia, String recMedia, String recQualityModes) {
    }

    /**
     * Transport settings record.
     *
     * @param playMode       the current play mode
     * @param recQualityMode the current recording quality mode
     * @since 1.0.0
     */
    public record TransportSettings(PlayMode playMode, String recQualityMode) {
    }

    /**
     * Returns current transport information.
     *
     * @param instanceId the instance ID
     * @return the transport info
     * @since 1.0.0
     */
    public TransportInfo getTransportInfo(int instanceId) {
        return new TransportInfo(transportState, transportStatus, currentSpeed);
    }

    /**
     * Returns current position information.
     *
     * @param instanceId the instance ID
     * @return the position info
     * @since 1.0.0
     */
    public PositionInfo getPositionInfo(int instanceId) {
        return new PositionInfo(
                currentTrack, trackDuration, currentUriMetadata, currentUri,
                relTime, absTime, 0, 0
        );
    }

    /**
     * Returns current media information.
     *
     * @param instanceId the instance ID
     * @return the media info
     * @since 1.0.0
     */
    public MediaInfo getMediaInfo(int instanceId) {
        String playMedium = currentUri.isEmpty() ? "NONE" : "NETWORK";
        return new MediaInfo(
                nrTracks, mediaDuration, currentUri, currentUriMetadata,
                nextUri, nextUriMetadata, playMedium,
                "NOT_IMPLEMENTED", "NOT_IMPLEMENTED"
        );
    }

    /**
     * Updates the current playback position (called by the renderer's playback engine).
     *
     * @param position the current position
     * @param duration the total track duration
     * @since 1.0.0
     */
    public void updatePosition(Duration position, Duration duration) {
        this.relTime = position;
        this.absTime = position;
        this.trackDuration = duration;
        this.mediaDuration = duration;
        fireEvent(new PlaybackEvent.PositionChanged(position, duration));
    }

    /**
     * Signals that playback has completed naturally.
     *
     * <p>If a next URI has been set via {@link #setNextAVTransportURI}, the transport
     * will automatically transition to playing the next URI (gapless playback).
     *
     * @since 1.0.0
     */
    public void playbackCompleted() {
        lock.lock();
        try {
            if (!nextUri.isEmpty()) {
                // Gapless transition to next URI
                this.currentUri = nextUri;
                this.currentUriMetadata = nextUriMetadata;
                this.nextUri = "";
                this.nextUriMetadata = "";
                this.relTime = Duration.ZERO;
                this.absTime = Duration.ZERO;
                this.transportState = TransportState.PLAYING;
                fireEvent(new PlaybackEvent.PlayStarted(currentUri, currentUriMetadata));
                return;
            }
            this.transportState = TransportState.STOPPED;
        } finally {
            lock.unlock();
        }
        fireEvent(new PlaybackEvent.PlayCompleted());
    }

    /**
     * Adds a playback listener.
     *
     * @param listener the listener to add
     * @since 1.0.0
     */
    public void addPlaybackListener(PlaybackListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    /**
     * Removes a playback listener.
     *
     * @param listener the listener to remove
     * @since 1.0.0
     */
    public void removePlaybackListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns the current transport state.
     *
     * @return the transport state
     * @since 1.0.0
     */
    public TransportState getTransportState() {
        return transportState;
    }

    /**
     * Generates the SCPD XML for this service.
     *
     * @return the SCPD XML string
     * @since 1.0.0
     */
    public String generateScpd() {
        return """
                <?xml version="1.0"?>
                <scpd xmlns="urn:schemas-upnp-org:service-1-0">
                  <specVersion><major>1</major><minor>0</minor></specVersion>
                  <actionList>
                    <action>
                      <name>SetAVTransportURI</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>CurrentURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>
                        <argument><name>CurrentURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>Play</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>Speed</name><direction>in</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>Pause</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>Stop</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>Seek</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>Unit</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekMode</relatedStateVariable></argument>
                        <argument><name>Target</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekTarget</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>Next</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>Previous</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetTransportInfo</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>CurrentTransportState</name><direction>out</direction><relatedStateVariable>TransportState</relatedStateVariable></argument>
                        <argument><name>CurrentTransportStatus</name><direction>out</direction><relatedStateVariable>TransportStatus</relatedStateVariable></argument>
                        <argument><name>CurrentSpeed</name><direction>out</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetPositionInfo</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>Track</name><direction>out</direction><relatedStateVariable>CurrentTrack</relatedStateVariable></argument>
                        <argument><name>TrackDuration</name><direction>out</direction><relatedStateVariable>CurrentTrackDuration</relatedStateVariable></argument>
                        <argument><name>TrackMetaData</name><direction>out</direction><relatedStateVariable>CurrentTrackMetaData</relatedStateVariable></argument>
                        <argument><name>TrackURI</name><direction>out</direction><relatedStateVariable>CurrentTrackURI</relatedStateVariable></argument>
                        <argument><name>RelTime</name><direction>out</direction><relatedStateVariable>RelativeTimePosition</relatedStateVariable></argument>
                        <argument><name>AbsTime</name><direction>out</direction><relatedStateVariable>AbsoluteTimePosition</relatedStateVariable></argument>
                        <argument><name>RelCount</name><direction>out</direction><relatedStateVariable>RelativeCounterPosition</relatedStateVariable></argument>
                        <argument><name>AbsCount</name><direction>out</direction><relatedStateVariable>AbsoluteCounterPosition</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>SetNextAVTransportURI</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>NextURI</name><direction>in</direction><relatedStateVariable>NextAVTransportURI</relatedStateVariable></argument>
                        <argument><name>NextURIMetaData</name><direction>in</direction><relatedStateVariable>NextAVTransportURIMetaData</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetDeviceCapabilities</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>PlayMedia</name><direction>out</direction><relatedStateVariable>PossiblePlaybackStorageMedia</relatedStateVariable></argument>
                        <argument><name>RecMedia</name><direction>out</direction><relatedStateVariable>PossibleRecordStorageMedia</relatedStateVariable></argument>
                        <argument><name>RecQualityModes</name><direction>out</direction><relatedStateVariable>PossibleRecordQualityModes</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetTransportSettings</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>PlayMode</name><direction>out</direction><relatedStateVariable>CurrentPlayMode</relatedStateVariable></argument>
                        <argument><name>RecQualityMode</name><direction>out</direction><relatedStateVariable>CurrentRecordQualityMode</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetMediaInfo</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>NrTracks</name><direction>out</direction><relatedStateVariable>NumberOfTracks</relatedStateVariable></argument>
                        <argument><name>MediaDuration</name><direction>out</direction><relatedStateVariable>CurrentMediaDuration</relatedStateVariable></argument>
                        <argument><name>CurrentURI</name><direction>out</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>
                        <argument><name>CurrentURIMetaData</name><direction>out</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>
                        <argument><name>NextURI</name><direction>out</direction><relatedStateVariable>NextAVTransportURI</relatedStateVariable></argument>
                        <argument><name>NextURIMetaData</name><direction>out</direction><relatedStateVariable>NextAVTransportURIMetaData</relatedStateVariable></argument>
                        <argument><name>PlayMedium</name><direction>out</direction><relatedStateVariable>PlaybackStorageMedium</relatedStateVariable></argument>
                        <argument><name>RecordMedium</name><direction>out</direction><relatedStateVariable>RecordStorageMedium</relatedStateVariable></argument>
                        <argument><name>WriteStatus</name><direction>out</direction><relatedStateVariable>RecordMediumWriteStatus</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                  </actionList>
                  <serviceStateTable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_SeekMode</name><dataType>string</dataType>
                      <allowedValueList><allowedValue>TRACK_NR</allowedValue><allowedValue>ABS_TIME</allowedValue><allowedValue>REL_TIME</allowedValue></allowedValueList>
                    </stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_SeekTarget</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="yes"><name>TransportState</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>TransportStatus</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>TransportPlaySpeed</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>AVTransportURI</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>AVTransportURIMetaData</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>CurrentTrack</name><dataType>ui4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>CurrentTrackDuration</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>CurrentTrackMetaData</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>CurrentTrackURI</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>RelativeTimePosition</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>AbsoluteTimePosition</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>RelativeCounterPosition</name><dataType>i4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>AbsoluteCounterPosition</name><dataType>i4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>NumberOfTracks</name><dataType>ui4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>CurrentMediaDuration</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>NextAVTransportURI</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>NextAVTransportURIMetaData</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>PlaybackStorageMedium</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>RecordStorageMedium</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>RecordMediumWriteStatus</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>PossiblePlaybackStorageMedia</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>PossibleRecordStorageMedia</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>PossibleRecordQualityModes</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>CurrentPlayMode</name><dataType>string</dataType>
                      <allowedValueList><allowedValue>NORMAL</allowedValue><allowedValue>SHUFFLE</allowedValue><allowedValue>REPEAT_ONE</allowedValue><allowedValue>REPEAT_ALL</allowedValue></allowedValueList>
                    </stateVariable>
                    <stateVariable sendEvents="no"><name>CurrentRecordQualityMode</name><dataType>string</dataType></stateVariable>
                  </serviceStateTable>
                </scpd>
                """;
    }

    private void fireEvent(PlaybackEvent event) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onPlaybackEvent(event);
            } catch (Exception e) {
                // Swallow listener exceptions to avoid disrupting playback
            }
        }
    }

    private Duration parseDuration(String target) {
        if (target == null || target.isEmpty()) {
            return Duration.ZERO;
        }
        return ssg.legoflow.upnp.mediaserver.ContentItem.parseDuration(target);
    }
}
