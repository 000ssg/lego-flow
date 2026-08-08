package ssg.legoflow.upnp.mediarenderer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UPnP RenderingControl:1 service implementation.
 *
 * <p>Manages audio output settings (volume, mute) for a media renderer.
 * Supports multiple audio channels: Master, LF (Left Front), RF (Right Front).
 *
 * @since 0.1.0
 */
public class RenderingControl {

    /** UPnP service type for RenderingControl:1. */
    public static final String SERVICE_TYPE = "urn:schemas-upnp-org:service:RenderingControl:1";

    /** UPnP service ID for RenderingControl. */
    public static final String SERVICE_ID = "urn:upnp-org:serviceId:RenderingControl";

    /** Master audio channel. */
    public static final String CHANNEL_MASTER = "Master";

    /** Left Front audio channel. */
    public static final String CHANNEL_LF = "LF";

    /** Right Front audio channel. */
    public static final String CHANNEL_RF = "RF";

    /** Center Front audio channel. */
    public static final String CHANNEL_CF = "CF";

    /** Low Frequency Enhancement (subwoofer) audio channel. */
    public static final String CHANNEL_LFE = "LFE";

    /** Left Surround audio channel. */
    public static final String CHANNEL_LS = "LS";

    /** Right Surround audio channel. */
    public static final String CHANNEL_RS = "RS";

    /** All recognized audio channels. @since 0.1.0 */
    public static final List<String> ALL_CHANNELS = List.of(
            CHANNEL_MASTER, CHANNEL_LF, CHANNEL_RF, CHANNEL_CF,
            CHANNEL_LFE, CHANNEL_LS, CHANNEL_RS
    );

    /** Minimum volume level. */
    public static final int MIN_VOLUME = 0;

    /** Maximum volume level. */
    public static final int MAX_VOLUME = 100;

    /** Minimum brightness/contrast/color level. */
    public static final int MIN_IMAGE_CONTROL = 0;

    /** Maximum brightness/contrast/color level. */
    public static final int MAX_IMAGE_CONTROL = 100;

    private final Map<String, Integer> volumes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> muteStates = new ConcurrentHashMap<>();
    private volatile int brightness = 50;
    private volatile int contrast = 50;
    private volatile int color = 50;
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new RenderingControl service with default settings.
     *
     * @since 0.1.0
     */
    public RenderingControl() {
        for (String channel : ALL_CHANNELS) {
            volumes.put(channel, 50);
            muteStates.put(channel, false);
        }
    }

    /**
     * Returns the volume for the specified channel.
     *
     * @param instanceId the instance ID
     * @param channel    the audio channel
     * @return the volume level (0-100)
     * @since 0.1.0
     */
    public int getVolume(int instanceId, String channel) {
        Objects.requireNonNull(channel, "channel must not be null");
        return volumes.getOrDefault(channel, 50);
    }

    /**
     * Sets the volume for the specified channel.
     *
     * @param instanceId    the instance ID
     * @param channel       the audio channel
     * @param desiredVolume the desired volume level (0-100)
     * @throws IllegalArgumentException if volume is out of range
     * @since 0.1.0
     */
    public void setVolume(int instanceId, String channel, int desiredVolume) {
        Objects.requireNonNull(channel, "channel must not be null");
        if (desiredVolume < MIN_VOLUME || desiredVolume > MAX_VOLUME) {
            throw new IllegalArgumentException(
                    "Volume must be between " + MIN_VOLUME + " and " + MAX_VOLUME + ": " + desiredVolume);
        }
        volumes.put(channel, desiredVolume);
        boolean muted = muteStates.getOrDefault(channel, false);
        fireEvent(new PlaybackEvent.VolumeChanged(desiredVolume, muted));
    }

    /**
     * Returns the mute state for the specified channel.
     *
     * @param instanceId the instance ID
     * @param channel    the audio channel
     * @return true if muted
     * @since 0.1.0
     */
    public boolean getMute(int instanceId, String channel) {
        Objects.requireNonNull(channel, "channel must not be null");
        return muteStates.getOrDefault(channel, false);
    }

    /**
     * Sets the mute state for the specified channel.
     *
     * @param instanceId  the instance ID
     * @param channel     the audio channel
     * @param desiredMute true to mute, false to unmute
     * @since 0.1.0
     */
    public void setMute(int instanceId, String channel, boolean desiredMute) {
        Objects.requireNonNull(channel, "channel must not be null");
        muteStates.put(channel, desiredMute);
        int volume = volumes.getOrDefault(channel, 50);
        fireEvent(new PlaybackEvent.VolumeChanged(volume, desiredMute));
    }

    /**
     * Adds a playback listener for volume/mute change events.
     *
     * @param listener the listener to add
     * @since 0.1.0
     */
    public void addPlaybackListener(PlaybackListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
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
     * Returns the current brightness level.
     *
     * @param instanceId the instance ID
     * @return the brightness level (0-100)
     * @since 0.1.0
     */
    public int getBrightness(int instanceId) {
        return brightness;
    }

    /**
     * Sets the brightness level.
     *
     * @param instanceId       the instance ID
     * @param desiredBrightness the desired brightness level (0-100)
     * @throws IllegalArgumentException if the value is out of range
     * @since 0.1.0
     */
    public void setBrightness(int instanceId, int desiredBrightness) {
        if (desiredBrightness < MIN_IMAGE_CONTROL || desiredBrightness > MAX_IMAGE_CONTROL) {
            throw new IllegalArgumentException(
                    "Brightness must be between " + MIN_IMAGE_CONTROL + " and " + MAX_IMAGE_CONTROL + ": " + desiredBrightness);
        }
        this.brightness = desiredBrightness;
    }

    /**
     * Returns the current contrast level.
     *
     * @param instanceId the instance ID
     * @return the contrast level (0-100)
     * @since 0.1.0
     */
    public int getContrast(int instanceId) {
        return contrast;
    }

    /**
     * Sets the contrast level.
     *
     * @param instanceId     the instance ID
     * @param desiredContrast the desired contrast level (0-100)
     * @throws IllegalArgumentException if the value is out of range
     * @since 0.1.0
     */
    public void setContrast(int instanceId, int desiredContrast) {
        if (desiredContrast < MIN_IMAGE_CONTROL || desiredContrast > MAX_IMAGE_CONTROL) {
            throw new IllegalArgumentException(
                    "Contrast must be between " + MIN_IMAGE_CONTROL + " and " + MAX_IMAGE_CONTROL + ": " + desiredContrast);
        }
        this.contrast = desiredContrast;
    }

    /**
     * Returns the current color saturation level.
     *
     * @param instanceId the instance ID
     * @return the color saturation level (0-100)
     * @since 0.1.0
     */
    public int getColor(int instanceId) {
        return color;
    }

    /**
     * Sets the color saturation level.
     *
     * @param instanceId   the instance ID
     * @param desiredColor the desired color saturation level (0-100)
     * @throws IllegalArgumentException if the value is out of range
     * @since 0.1.0
     */
    public void setColor(int instanceId, int desiredColor) {
        if (desiredColor < MIN_IMAGE_CONTROL || desiredColor > MAX_IMAGE_CONTROL) {
            throw new IllegalArgumentException(
                    "Color must be between " + MIN_IMAGE_CONTROL + " and " + MAX_IMAGE_CONTROL + ": " + desiredColor);
        }
        this.color = desiredColor;
    }

    /**
     * Returns the list of supported audio channel names.
     *
     * @return unmodifiable list of channel names
     * @since 0.1.0
     */
    public List<String> getSupportedChannels() {
        return ALL_CHANNELS;
    }

    /**
     * Generates the SCPD XML for this service.
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
                      <name>GetVolume</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
                        <argument><name>CurrentVolume</name><direction>out</direction><relatedStateVariable>Volume</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>SetVolume</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
                        <argument><name>DesiredVolume</name><direction>in</direction><relatedStateVariable>Volume</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetMute</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
                        <argument><name>CurrentMute</name><direction>out</direction><relatedStateVariable>Mute</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>SetMute</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
                        <argument><name>DesiredMute</name><direction>in</direction><relatedStateVariable>Mute</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetBrightness</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>CurrentBrightness</name><direction>out</direction><relatedStateVariable>Brightness</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>SetBrightness</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>DesiredBrightness</name><direction>in</direction><relatedStateVariable>Brightness</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetContrast</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>CurrentContrast</name><direction>out</direction><relatedStateVariable>Contrast</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>SetContrast</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>DesiredContrast</name><direction>in</direction><relatedStateVariable>Contrast</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetColor</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>CurrentColor</name><direction>out</direction><relatedStateVariable>ColorTemperature</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>SetColor</name>
                      <argumentList>
                        <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                        <argument><name>DesiredColor</name><direction>in</direction><relatedStateVariable>ColorTemperature</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                  </actionList>
                  <serviceStateTable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Channel</name><dataType>string</dataType>
                      <allowedValueList><allowedValue>Master</allowedValue><allowedValue>LF</allowedValue><allowedValue>RF</allowedValue><allowedValue>CF</allowedValue><allowedValue>LFE</allowedValue><allowedValue>LS</allowedValue><allowedValue>RS</allowedValue></allowedValueList>
                    </stateVariable>
                    <stateVariable sendEvents="yes"><name>Volume</name><dataType>ui2</dataType>
                      <allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange>
                    </stateVariable>
                    <stateVariable sendEvents="yes"><name>Mute</name><dataType>boolean</dataType></stateVariable>
                    <stateVariable sendEvents="yes"><name>Brightness</name><dataType>ui2</dataType>
                      <allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange>
                    </stateVariable>
                    <stateVariable sendEvents="yes"><name>Contrast</name><dataType>ui2</dataType>
                      <allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange>
                    </stateVariable>
                    <stateVariable sendEvents="yes"><name>ColorTemperature</name><dataType>ui2</dataType>
                      <allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange>
                    </stateVariable>
                  </serviceStateTable>
                </scpd>
                """;
    }

    private void fireEvent(PlaybackEvent event) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onPlaybackEvent(event);
            } catch (Exception e) {
                // Swallow listener exceptions
            }
        }
    }
}
