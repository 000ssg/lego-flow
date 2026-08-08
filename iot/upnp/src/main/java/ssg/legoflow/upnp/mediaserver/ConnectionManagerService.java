package ssg.legoflow.upnp.mediaserver;

import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UPnP ConnectionManager:1 service implementation.
 *
 * <p>Manages connection information and protocol capabilities for UPnP AV devices.
 * Media servers advertise their source protocols; media renderers advertise their
 * sink protocols.
 *
 * @since 0.1.0
 */
public class ConnectionManagerService {

    /** UPnP service type for ConnectionManager:1. */
    public static final String SERVICE_TYPE = "urn:schemas-upnp-org:service:ConnectionManager:1";

    /** UPnP service ID for ConnectionManager. */
    public static final String SERVICE_ID = "urn:upnp-org:serviceId:ConnectionManager";

    /**
     * Connection information record.
     *
     * @param connectionId the connection identifier
     * @param rcsId        the RenderingControl service instance ID
     * @param avTransportId the AVTransport service instance ID
     * @param protocolInfo the protocol info for this connection
     * @param peerConnectionManager the peer connection manager URI
     * @param peerConnectionId the peer connection ID
     * @param direction the direction ("Input" or "Output")
     * @param status the connection status ("OK", "ContentFormatMismatch", "InsufficientBandwidth", "Unknown")
     * @since 0.1.0
     */
    public record ConnectionInfo(
            int connectionId,
            int rcsId,
            int avTransportId,
            DlnaProtocolInfo protocolInfo,
            String peerConnectionManager,
            int peerConnectionId,
            String direction,
            String status
    ) {
    }

    private final List<DlnaProtocolInfo> sourceProtocols = new CopyOnWriteArrayList<>();
    private final List<DlnaProtocolInfo> sinkProtocols = new CopyOnWriteArrayList<>();
    private final List<ConnectionInfo> connections = new CopyOnWriteArrayList<>();

    /**
     * Creates a new ConnectionManager service.
     *
     * @since 0.1.0
     */
    public ConnectionManagerService() {
    }

    /**
     * Returns the source and sink protocol info strings.
     *
     * @return a two-element array: [sourceProtocols, sinkProtocols] as comma-separated strings
     * @since 0.1.0
     */
    public String[] getProtocolInfo() {
        return new String[]{
                formatProtocolInfoList(sourceProtocols),
                formatProtocolInfoList(sinkProtocols)
        };
    }

    /**
     * Returns the source protocol info list.
     *
     * @return unmodifiable list of source protocols
     * @since 0.1.0
     */
    public List<DlnaProtocolInfo> getSourceProtocols() {
        return Collections.unmodifiableList(sourceProtocols);
    }

    /**
     * Returns the sink protocol info list.
     *
     * @return unmodifiable list of sink protocols
     * @since 0.1.0
     */
    public List<DlnaProtocolInfo> getSinkProtocols() {
        return Collections.unmodifiableList(sinkProtocols);
    }

    /**
     * Adds a source protocol (media server output capability).
     *
     * @param protocolInfo the protocol info to add
     * @since 0.1.0
     */
    public void addSourceProtocol(DlnaProtocolInfo protocolInfo) {
        Objects.requireNonNull(protocolInfo, "protocolInfo must not be null");
        sourceProtocols.add(protocolInfo);
    }

    /**
     * Adds a sink protocol (media renderer input capability).
     *
     * @param protocolInfo the protocol info to add
     * @since 0.1.0
     */
    public void addSinkProtocol(DlnaProtocolInfo protocolInfo) {
        Objects.requireNonNull(protocolInfo, "protocolInfo must not be null");
        sinkProtocols.add(protocolInfo);
    }

    /**
     * Returns the current connection IDs as a comma-separated string.
     *
     * @return the connection IDs string
     * @since 0.1.0
     */
    public String getCurrentConnectionIDs() {
        if (connections.isEmpty()) {
            return "0";
        }
        var sb = new StringBuilder();
        for (int i = 0; i < connections.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(connections.get(i).connectionId());
        }
        return sb.toString();
    }

    /**
     * Returns connection info for the specified connection ID.
     *
     * @param connectionId the connection ID
     * @return the connection info
     * @throws IllegalArgumentException if the connection ID is not found
     * @since 0.1.0
     */
    public ConnectionInfo getCurrentConnectionInfo(int connectionId) {
        for (ConnectionInfo info : connections) {
            if (info.connectionId() == connectionId) {
                return info;
            }
        }
        // Default connection 0
        if (connectionId == 0) {
            return new ConnectionInfo(0, -1, -1, null, "", -1, "Output", "OK");
        }
        throw new IllegalArgumentException("Unknown connection ID: " + connectionId);
    }

    /**
     * Prepares a new connection for the specified remote protocol info.
     *
     * <p>Validates that the remote protocol info is compatible with this device's
     * supported protocols for the given direction. If compatible, creates a new
     * connection and returns its info.
     *
     * @param remoteProtocolInfo    the protocol info of the remote device
     * @param peerConnectionManager the peer connection manager service URI
     * @param peerConnectionId      the peer connection ID
     * @param direction             the connection direction ("Input" or "Output")
     * @return the new connection info
     * @throws IllegalArgumentException if the protocol is incompatible
     * @since 0.1.0
     */
    public ConnectionInfo prepareForConnection(DlnaProtocolInfo remoteProtocolInfo,
                                               String peerConnectionManager,
                                               int peerConnectionId,
                                               String direction) {
        List<DlnaProtocolInfo> protocols = "Output".equals(direction) ? sourceProtocols : sinkProtocols;
        boolean compatible = protocols.stream()
                .anyMatch(p -> p.isCompatibleWith(remoteProtocolInfo));
        if (!compatible && !protocols.isEmpty()) {
            throw new IllegalArgumentException("Incompatible protocol: " + remoteProtocolInfo
                    + " (supported: " + formatProtocolInfoList(protocols) + ")");
        }
        int connectionId = connections.size() + 1;
        var info = new ConnectionInfo(
                connectionId, 0, 0, remoteProtocolInfo,
                peerConnectionManager, peerConnectionId, direction, "OK");
        connections.add(info);
        return info;
    }

    /**
     * Completes and removes a connection by its ID.
     *
     * @param connectionId the connection ID to complete
     * @since 0.1.0
     */
    public void connectionComplete(int connectionId) {
        connections.removeIf(c -> c.connectionId() == connectionId);
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
                      <name>GetProtocolInfo</name>
                      <argumentList>
                        <argument><name>Source</name><direction>out</direction><relatedStateVariable>SourceProtocolInfo</relatedStateVariable></argument>
                        <argument><name>Sink</name><direction>out</direction><relatedStateVariable>SinkProtocolInfo</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetCurrentConnectionIDs</name>
                      <argumentList>
                        <argument><name>ConnectionIDs</name><direction>out</direction><relatedStateVariable>CurrentConnectionIDs</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>GetCurrentConnectionInfo</name>
                      <argumentList>
                        <argument><name>ConnectionID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>
                        <argument><name>RcsID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_RcsID</relatedStateVariable></argument>
                        <argument><name>AVTransportID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_AVTransportID</relatedStateVariable></argument>
                        <argument><name>ProtocolInfo</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ProtocolInfo</relatedStateVariable></argument>
                        <argument><name>PeerConnectionManager</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionManager</relatedStateVariable></argument>
                        <argument><name>PeerConnectionID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>
                        <argument><name>Direction</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Direction</relatedStateVariable></argument>
                        <argument><name>Status</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionStatus</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>PrepareForConnection</name>
                      <argumentList>
                        <argument><name>RemoteProtocolInfo</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ProtocolInfo</relatedStateVariable></argument>
                        <argument><name>PeerConnectionManager</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionManager</relatedStateVariable></argument>
                        <argument><name>PeerConnectionID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>
                        <argument><name>Direction</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Direction</relatedStateVariable></argument>
                        <argument><name>ConnectionID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>
                        <argument><name>AVTransportID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_AVTransportID</relatedStateVariable></argument>
                        <argument><name>RcsID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_RcsID</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                    <action>
                      <name>ConnectionComplete</name>
                      <argumentList>
                        <argument><name>ConnectionID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>
                      </argumentList>
                    </action>
                  </actionList>
                  <serviceStateTable>
                    <stateVariable sendEvents="yes"><name>SourceProtocolInfo</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="yes"><name>SinkProtocolInfo</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="yes"><name>CurrentConnectionIDs</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_ConnectionStatus</name><dataType>string</dataType>
                      <allowedValueList><allowedValue>OK</allowedValue><allowedValue>ContentFormatMismatch</allowedValue><allowedValue>InsufficientBandwidth</allowedValue><allowedValue>UnreliableChannel</allowedValue><allowedValue>Unknown</allowedValue></allowedValueList>
                    </stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_ConnectionManager</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Direction</name><dataType>string</dataType>
                      <allowedValueList><allowedValue>Input</allowedValue><allowedValue>Output</allowedValue></allowedValueList>
                    </stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_ProtocolInfo</name><dataType>string</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_ConnectionID</name><dataType>i4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_AVTransportID</name><dataType>i4</dataType></stateVariable>
                    <stateVariable sendEvents="no"><name>A_ARG_TYPE_RcsID</name><dataType>i4</dataType></stateVariable>
                  </serviceStateTable>
                </scpd>
                """;
    }

    private String formatProtocolInfoList(List<DlnaProtocolInfo> protocols) {
        var sb = new StringBuilder();
        for (int i = 0; i < protocols.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(protocols.get(i).toString());
        }
        return sb.toString();
    }
}
