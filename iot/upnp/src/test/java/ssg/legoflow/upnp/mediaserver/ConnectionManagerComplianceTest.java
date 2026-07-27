package ssg.legoflow.upnp.mediaserver;

import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Compliance tests for ConnectionManager full lifecycle:
 * PrepareForConnection, ConnectionComplete, GetCurrentConnectionIDs, GetCurrentConnectionInfo.
 *
 * @since 1.0.0
 */
class ConnectionManagerComplianceTest {

    private ConnectionManagerService cm;

    @BeforeEach
    void setUp() {
        cm = new ConnectionManagerService();
        cm.addSourceProtocol(DlnaProtocolInfo.httpGet("audio/mpeg", "MP3"));
        cm.addSourceProtocol(DlnaProtocolInfo.httpGet("video/mp4", "AVC_MP4_BL_CIF15"));
        cm.addSinkProtocol(DlnaProtocolInfo.httpGet("audio/mpeg", "MP3"));
    }

    @Test
    void testPrepareForConnection() {
        // When
        var info = cm.prepareForConnection(
                DlnaProtocolInfo.httpGet("audio/mpeg", "MP3"),
                "http://remote/cm", 0, "Output");

        // Then
        assertThat(info).isNotNull();
        assertThat(info.connectionId()).isGreaterThan(0);
        assertThat(info.direction()).isEqualTo("Output");
        assertThat(info.status()).isEqualTo("OK");
    }

    @Test
    void testConnectionComplete() {
        // Given: prepare a connection
        var info = cm.prepareForConnection(
                DlnaProtocolInfo.httpGet("audio/mpeg", "MP3"),
                "http://remote/cm", 0, "Output");
        int connId = info.connectionId();

        // When: complete it
        cm.connectionComplete(connId);

        // Then: default connection 0 should still work
        var defaultInfo = cm.getCurrentConnectionInfo(0);
        assertThat(defaultInfo).isNotNull();
        assertThat(defaultInfo.connectionId()).isEqualTo(0);
    }

    @Test
    void testGetCurrentConnectionIDs() {
        // Initially
        assertThat(cm.getCurrentConnectionIDs()).isEqualTo("0");

        // After creating connections
        cm.prepareForConnection(DlnaProtocolInfo.httpGet("audio/mpeg", "MP3"),
                "", 0, "Output");
        cm.prepareForConnection(DlnaProtocolInfo.httpGet("video/mp4", "AVC_MP4_BL_CIF15"),
                "", 0, "Output");

        String ids = cm.getCurrentConnectionIDs();
        assertThat(ids).contains("1");
        assertThat(ids).contains("2");
    }

    @Test
    void testGetCurrentConnectionInfo() {
        // Given
        var proto = DlnaProtocolInfo.httpGet("audio/mpeg", "MP3");
        var info = cm.prepareForConnection(proto, "http://peer/cm", 5, "Output");

        // When
        var retrieved = cm.getCurrentConnectionInfo(info.connectionId());

        // Then
        assertThat(retrieved.protocolInfo()).isEqualTo(proto);
        assertThat(retrieved.peerConnectionManager()).isEqualTo("http://peer/cm");
        assertThat(retrieved.peerConnectionId()).isEqualTo(5);
        assertThat(retrieved.direction()).isEqualTo("Output");
        assertThat(retrieved.status()).isEqualTo("OK");
    }

    @Test
    void testGetDefaultConnectionInfo() {
        // Connection 0 is the default
        var info = cm.getCurrentConnectionInfo(0);
        assertThat(info.connectionId()).isEqualTo(0);
        assertThat(info.rcsId()).isEqualTo(-1);
        assertThat(info.avTransportId()).isEqualTo(-1);
        assertThat(info.direction()).isEqualTo("Output");
        assertThat(info.status()).isEqualTo("OK");
    }

    @Test
    void testGetConnectionInfoUnknownThrows() {
        assertThatThrownBy(() -> cm.getCurrentConnectionInfo(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown connection ID");
    }

    @Test
    void testPrepareForConnectionInputDirection() {
        // When: input direction (for sink protocols)
        var info = cm.prepareForConnection(
                DlnaProtocolInfo.httpGet("audio/mpeg", "MP3"),
                "http://server/cm", 1, "Input");

        // Then
        assertThat(info.direction()).isEqualTo("Input");
    }

    @Test
    void testConnectionLifecycle() {
        // Create 3 connections
        var c1 = cm.prepareForConnection(DlnaProtocolInfo.httpGet("audio/mpeg", "MP3"), "", 0, "Output");
        var c2 = cm.prepareForConnection(DlnaProtocolInfo.httpGet("audio/mpeg", "MP3"), "", 0, "Output");
        var c3 = cm.prepareForConnection(DlnaProtocolInfo.httpGet("audio/mpeg", "MP3"), "", 0, "Output");

        // Complete the middle one
        cm.connectionComplete(c2.connectionId());

        // IDs should still list c1 and c3
        String ids = cm.getCurrentConnectionIDs();
        assertThat(ids).contains(String.valueOf(c1.connectionId()));
        assertThat(ids).contains(String.valueOf(c3.connectionId()));
    }

    @Test
    void testGetProtocolInfo() {
        String[] info = cm.getProtocolInfo();
        assertThat(info).hasSize(2);
        assertThat(info[0]).contains("audio/mpeg"); // source
        assertThat(info[1]).contains("audio/mpeg"); // sink
    }

    @Test
    void testScpdContainsAllActions() {
        var scpd = cm.generateScpd();
        assertThat(scpd).contains("<name>GetProtocolInfo</name>");
        assertThat(scpd).contains("<name>PrepareForConnection</name>");
        assertThat(scpd).contains("<name>ConnectionComplete</name>");
        assertThat(scpd).contains("<name>GetCurrentConnectionIDs</name>");
        assertThat(scpd).contains("<name>GetCurrentConnectionInfo</name>");
    }
}
