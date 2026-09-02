package ssg.legoflow.upnp.mediarenderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.upnp.mediaserver.ConnectionManagerService;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
/**
 * Integration tests for {@link MediaRendererDevice} action dispatch,
 * verifying that all new actions are correctly wired through handleAction.
 *
 * @since 0.1.0
 */
class MediaRendererActionDispatchTest {

    private MediaRendererDevice device;

    @BeforeEach
    void setUp() {
        device = new MediaRendererDevice("Test Renderer");
    }

    // --- AVTransport new actions ---

    @Test
    void testSetNextAVTransportURIAction() {
        device.handleAction(AvTransport.SERVICE_ID, "SetAVTransportURI",
                Map.of("InstanceID", "0", "CurrentURI", "http://a.mp3", "CurrentURIMetaData", ""));
        var result = device.handleAction(AvTransport.SERVICE_ID, "SetNextAVTransportURI",
                Map.of("InstanceID", "0", "NextURI", "http://b.mp3", "NextURIMetaData", "meta"));
        assertThat(result).isEmpty();
    }

    @Test
    void testGetDeviceCapabilitiesAction() {
        var result = device.handleAction(AvTransport.SERVICE_ID, "GetDeviceCapabilities",
                Map.of("InstanceID", "0"));
        assertThat(result).containsKey("PlayMedia");
        assertThat(result).containsKey("RecMedia");
        assertThat(result).containsKey("RecQualityModes");
    }

    @Test
    void testGetTransportSettingsAction() {
        var result = device.handleAction(AvTransport.SERVICE_ID, "GetTransportSettings",
                Map.of("InstanceID", "0"));
        assertThat(result).containsKey("PlayMode");
        assertThat(result.get("PlayMode")).isEqualTo("NORMAL");
        assertThat(result).containsKey("RecQualityMode");
    }

    // --- RenderingControl new actions ---

    @Test
    void testSetAndGetBrightnessAction() {
        device.handleAction(RenderingControl.SERVICE_ID, "SetBrightness",
                Map.of("InstanceID", "0", "DesiredBrightness", "80"));
        var result = device.handleAction(RenderingControl.SERVICE_ID, "GetBrightness",
                Map.of("InstanceID", "0"));
        assertThat(result.get("CurrentBrightness")).isEqualTo("80");
    }

    @Test
    void testSetAndGetContrastAction() {
        device.handleAction(RenderingControl.SERVICE_ID, "SetContrast",
                Map.of("InstanceID", "0", "DesiredContrast", "70"));
        var result = device.handleAction(RenderingControl.SERVICE_ID, "GetContrast",
                Map.of("InstanceID", "0"));
        assertThat(result.get("CurrentContrast")).isEqualTo("70");
    }

    @Test
    void testSetAndGetColorAction() {
        device.handleAction(RenderingControl.SERVICE_ID, "SetColor",
                Map.of("InstanceID", "0", "DesiredColor", "30"));
        var result = device.handleAction(RenderingControl.SERVICE_ID, "GetColor",
                Map.of("InstanceID", "0"));
        assertThat(result.get("CurrentColor")).isEqualTo("30");
    }

    // --- ConnectionManager new actions ---

    @Test
    void testPrepareForConnectionAction() {
        var result = device.handleAction(ConnectionManagerService.SERVICE_ID, "PrepareForConnection",
                Map.of("RemoteProtocolInfo", "http-get:*:audio/mpeg:*",
                        "PeerConnectionManager", "http://peer/cm",
                        "PeerConnectionID", "0",
                        "Direction", "Input"));
        assertThat(result).containsKey("ConnectionID");
        assertThat(result).containsKey("AVTransportID");
        assertThat(result).containsKey("RcsID");
    }

    @Test
    void testConnectionCompleteAction() {
        // First prepare a connection
        var prepResult = device.handleAction(ConnectionManagerService.SERVICE_ID, "PrepareForConnection",
                Map.of("RemoteProtocolInfo", "http-get:*:audio/mpeg:*",
                        "PeerConnectionManager", "",
                        "PeerConnectionID", "0",
                        "Direction", "Input"));
        String connId = prepResult.get("ConnectionID");

        // Then complete it
        var result = device.handleAction(ConnectionManagerService.SERVICE_ID, "ConnectionComplete",
                Map.of("ConnectionID", connId));
        assertThat(result).isEmpty();
    }

    @Test
    void testUnknownServiceThrows() {
        assertThatThrownBy(() -> device.handleAction("unknown:service", "Play", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnknownAvTransportActionThrows() {
        assertThatThrownBy(() -> device.handleAction(AvTransport.SERVICE_ID, "NonExistent", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnknownRenderingControlActionThrows() {
        assertThatThrownBy(() -> device.handleAction(RenderingControl.SERVICE_ID, "NonExistent", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
