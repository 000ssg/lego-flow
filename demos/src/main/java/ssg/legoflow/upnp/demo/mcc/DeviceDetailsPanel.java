package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.DeviceProxy;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.device.DeviceDescription;
import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import ssg.legoflow.upnp.mediaserver.ContentItem;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Panel displaying per-type device properties based on the selected device.
 *
 * <p>Uses a {@link CardLayout} to switch between views for different device types:
 * <ul>
 *   <li><b>Media Server</b> — device info, protocol info table</li>
 *   <li><b>Media Renderer</b> — device info, transport state, volume/mute</li>
 *   <li><b>Generic Device</b> — device info, services table</li>
 *   <li><b>Empty</b> — placeholder when no device is selected</li>
 * </ul>
 *
 * <p>All visual elements use the {@link DarkTheme} colour palette.
 *
 * @since 1.0.0
 */
public class DeviceDetailsPanel extends JPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_SERVER = "server";
    private static final String CARD_RENDERER = "renderer";
    private static final String CARD_DEVICE = "device";
    private static final String CARD_FAILED = "failed";

    private final CardLayout cardLayout;
    private final JPanel cardsPanel;

    // Server card components
    private final JLabel serverName = new JLabel();
    private final JLabel serverUdn = new JLabel();
    private final JLabel serverType = new JLabel();
    private final JLabel serverUrl = new JLabel();
    private final JLabel serverManufacturer = new JLabel();
    private final JLabel serverModel = new JLabel();
    private final DefaultTableModel protocolTableModel;

    // Renderer card components
    private final JLabel rendererName = new JLabel();
    private final JLabel rendererUdn = new JLabel();
    private final JLabel rendererType = new JLabel();
    private final JLabel rendererUrl = new JLabel();
    private final JLabel rendererManufacturer = new JLabel();
    private final JLabel rendererModel = new JLabel();
    private final JLabel rendererState = new JLabel();
    private final JLabel rendererTrack = new JLabel();
    private final JLabel rendererPosition = new JLabel();
    private final JLabel rendererVolume = new JLabel();

    // Generic device card components
    private final JLabel deviceName = new JLabel();
    private final JLabel deviceUdn = new JLabel();
    private final JLabel deviceType = new JLabel();
    private final JLabel deviceUrl = new JLabel();
    private final JLabel deviceManufacturer = new JLabel();
    private final JLabel deviceModel = new JLabel();
    private final DefaultTableModel servicesTableModel;

    // Failed device card components
    private final JLabel failedUdn = new JLabel();
    private final JLabel failedLocation = new JLabel();
    private final JLabel failedTimestamp = new JLabel();
    private final JTextArea failedErrorText = new JTextArea();
    private final JTextArea failedResponseText = new JTextArea();

    /**
     * Creates a new device details panel.
     *
     * @since 1.0.0
     */
    public DeviceDetailsPanel() {
        setLayout(new BorderLayout());
        setBackground(DarkTheme.PANEL_BG);
        setBorder(DarkTheme.panelBorder("Device Details"));

        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBackground(DarkTheme.PANEL_BG);

        // Empty card
        var emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setBackground(DarkTheme.PANEL_BG);
        var emptyLabel = new JLabel("Select a device to view details");
        emptyLabel.setForeground(DarkTheme.MUTED_TEXT);
        emptyPanel.add(emptyLabel);
        cardsPanel.add(emptyPanel, CARD_EMPTY);

        // Server card
        protocolTableModel = new DefaultTableModel(
                new String[]{"Protocol", "Network", "Content Format", "Additional Info"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        cardsPanel.add(buildServerCard(), CARD_SERVER);

        // Renderer card
        cardsPanel.add(buildRendererCard(), CARD_RENDERER);

        // Generic device card
        servicesTableModel = new DefaultTableModel(
                new String[]{"Service Type", "Service ID"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        cardsPanel.add(buildDeviceCard(), CARD_DEVICE);

        // Failed device card
        cardsPanel.add(buildFailedDeviceCard(), CARD_FAILED);

        add(cardsPanel, BorderLayout.CENTER);
        cardLayout.show(cardsPanel, CARD_EMPTY);
    }

    /**
     * Shows details for the given device, switching to the appropriate card.
     *
     * @param device the device to display details for
     * @since 1.0.0
     */
    public void showDevice(DeviceProxy device) {
        if (device == null) {
            showEmpty();
            return;
        }
        if (device instanceof MediaServerProxy server) {
            populateServerCard(server);
            cardLayout.show(cardsPanel, CARD_SERVER);
        } else if (device instanceof MediaRendererProxy renderer) {
            populateRendererCard(renderer);
            cardLayout.show(cardsPanel, CARD_RENDERER);
        } else {
            populateDeviceCard(device);
            cardLayout.show(cardsPanel, CARD_DEVICE);
        }
    }

    /**
     * Shows the empty placeholder card.
     *
     * @since 1.0.0
     */
    public void showEmpty() {
        cardLayout.show(cardsPanel, CARD_EMPTY);
    }

    /**
     * Shows details for a failed (unrecognized) device, including the error message
     * and the raw response text from the device.
     *
     * @param failedDevice the failed device to display
     * @since 1.0.0
     */
    public void showFailedDevice(ControlPoint.FailedDevice failedDevice) {
        if (failedDevice == null) {
            showEmpty();
            return;
        }
        populateFailedDeviceCard(failedDevice);
        cardLayout.show(cardsPanel, CARD_FAILED);
    }

    // --- Card builders ---

    private JPanel buildServerCard() {
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(DarkTheme.PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        var infoPanel = buildInfoGrid(serverName, serverUdn, serverType,
                serverUrl, serverManufacturer, serverModel, "Media Server");

        var protocolTable = new JTable(protocolTableModel);
        protocolTable.setFillsViewportHeight(true);
        protocolTable.setRowHeight(20);
        DarkTheme.styleTable(protocolTable);

        var protocolPanel = new JPanel(new BorderLayout());
        protocolPanel.setBackground(DarkTheme.PANEL_BG);
        protocolPanel.setBorder(DarkTheme.panelBorder("Protocol Info"));
        var scrollPane = new JScrollPane(protocolTable);
        scrollPane.getViewport().setBackground(DarkTheme.BODY_BG);
        protocolPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(infoPanel, BorderLayout.NORTH);
        panel.add(protocolPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRendererCard() {
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(DarkTheme.PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        var infoPanel = buildInfoGrid(rendererName, rendererUdn, rendererType,
                rendererUrl, rendererManufacturer, rendererModel, "Media Renderer");

        // Transport section
        var transportPanel = new JPanel(new GridBagLayout());
        transportPanel.setBackground(DarkTheme.PANEL_BG);
        transportPanel.setBorder(DarkTheme.panelBorder("Transport"));
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addFieldToGrid(transportPanel, gbc, row++, "State:", rendererState);
        addFieldToGrid(transportPanel, gbc, row++, "Track:", rendererTrack);
        addFieldToGrid(transportPanel, gbc, row++, "Position:", rendererPosition);
        addFieldToGrid(transportPanel, gbc, row++, "Volume:", rendererVolume);

        // Add refresh button
        var refreshBtn = new JButton("Refresh");
        refreshBtn.setMargin(new Insets(2, 8, 2, 8));
        refreshBtn.setBackground(DarkTheme.PANEL_BG);
        refreshBtn.setForeground(DarkTheme.TEXT);
        refreshBtn.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> {
            // Re-read the current device (stored in label's client property)
            var device = (MediaRendererProxy) cardsPanel.getClientProperty("currentRenderer");
            if (device != null) {
                populateRendererCard(device);
            }
        });
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.EAST;
        transportPanel.add(refreshBtn, gbc);

        gbc.gridy = row + 1;
        gbc.weighty = 1.0;
        gbc.gridwidth = 1;
        transportPanel.add(Box.createVerticalGlue(), gbc);

        panel.add(infoPanel, BorderLayout.NORTH);
        panel.add(transportPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDeviceCard() {
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(DarkTheme.PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        var infoPanel = buildInfoGrid(deviceName, deviceUdn, deviceType,
                deviceUrl, deviceManufacturer, deviceModel, "Device");

        var servicesTable = new JTable(servicesTableModel);
        servicesTable.setFillsViewportHeight(true);
        servicesTable.setRowHeight(20);
        DarkTheme.styleTable(servicesTable);

        var servicesPanel = new JPanel(new BorderLayout());
        servicesPanel.setBackground(DarkTheme.PANEL_BG);
        servicesPanel.setBorder(DarkTheme.panelBorder("Services"));
        var scrollPane = new JScrollPane(servicesTable);
        scrollPane.getViewport().setBackground(DarkTheme.BODY_BG);
        servicesPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(infoPanel, BorderLayout.NORTH);
        panel.add(servicesPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFailedDeviceCard() {
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(DarkTheme.PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Info grid
        var infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBackground(DarkTheme.PANEL_BG);
        infoPanel.setBorder(DarkTheme.panelBorder("Unrecognized Device"));
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;
        addFieldToGrid(infoPanel, gbc, row++, "UDN:", failedUdn);
        addFieldToGrid(infoPanel, gbc, row++, "Location:", failedLocation);
        addFieldToGrid(infoPanel, gbc, row, "Failed At:", failedTimestamp);

        // Error section
        var errorPanel = new JPanel(new BorderLayout());
        errorPanel.setBackground(DarkTheme.PANEL_BG);
        errorPanel.setBorder(DarkTheme.panelBorder("Error"));
        failedErrorText.setEditable(false);
        failedErrorText.setLineWrap(true);
        failedErrorText.setWrapStyleWord(true);
        failedErrorText.setBackground(new Color(28, 25, 23)); // dark error bg
        failedErrorText.setForeground(DarkTheme.ERROR);
        failedErrorText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        failedErrorText.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var errorScroll = new JScrollPane(failedErrorText);
        errorScroll.setPreferredSize(new Dimension(0, 80));
        errorScroll.getViewport().setBackground(new Color(28, 25, 23));
        errorScroll.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        errorPanel.add(errorScroll, BorderLayout.CENTER);

        // Response text section
        var responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBackground(DarkTheme.PANEL_BG);
        responsePanel.setBorder(DarkTheme.panelBorder("Response Text"));
        failedResponseText.setEditable(false);
        failedResponseText.setLineWrap(true);
        failedResponseText.setWrapStyleWord(false);
        failedResponseText.setBackground(DarkTheme.BODY_BG);
        failedResponseText.setForeground(DarkTheme.SECONDARY_TEXT);
        failedResponseText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        failedResponseText.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var responseScroll = new JScrollPane(failedResponseText);
        responseScroll.getViewport().setBackground(DarkTheme.BODY_BG);
        responseScroll.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        responsePanel.add(responseScroll, BorderLayout.CENTER);

        // Assemble: info on top, error in middle, response text takes remaining space
        var topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setBackground(DarkTheme.PANEL_BG);
        topPanel.add(infoPanel, BorderLayout.NORTH);
        topPanel.add(errorPanel, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(responsePanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInfoGrid(JLabel nameLabel, JLabel udnLabel, JLabel typeLabel,
                                  JLabel urlLabel, JLabel mfgLabel, JLabel modelLabel,
                                  String title) {
        var panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.PANEL_BG);
        panel.setBorder(DarkTheme.panelBorder(title));
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addFieldToGrid(panel, gbc, row++, "Name:", nameLabel);
        addFieldToGrid(panel, gbc, row++, "UDN:", udnLabel);
        addFieldToGrid(panel, gbc, row++, "Type:", typeLabel);
        addFieldToGrid(panel, gbc, row++, "Base URL:", urlLabel);
        addFieldToGrid(panel, gbc, row++, "Manufacturer:", mfgLabel);
        addFieldToGrid(panel, gbc, row, "Model:", modelLabel);
        return panel;
    }

    private void addFieldToGrid(JPanel panel, GridBagConstraints gbc,
                                 int row, String label, JLabel valueLabel) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        var lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
        lbl.setForeground(DarkTheme.SECONDARY_TEXT);
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.PLAIN, 12f));
        valueLabel.setForeground(DarkTheme.TEXT);
        panel.add(valueLabel, gbc);
    }

    // --- Card population ---

    private void populateServerCard(MediaServerProxy server) {
        populateBaseInfo(server, serverName, serverUdn, serverType,
                serverUrl, serverManufacturer, serverModel);

        protocolTableModel.setRowCount(0);
        try {
            List<DlnaProtocolInfo> protocols = server.getProtocolInfo();
            for (DlnaProtocolInfo pi : protocols) {
                protocolTableModel.addRow(new Object[]{
                        pi.protocol(), pi.network(), pi.contentFormat(), pi.additionalInfo()
                });
            }
        } catch (Exception e) {
            protocolTableModel.addRow(new Object[]{"Error", "", "", e.getMessage()});
        }
    }

    private void populateRendererCard(MediaRendererProxy renderer) {
        cardsPanel.putClientProperty("currentRenderer", renderer);
        populateBaseInfo(renderer, rendererName, rendererUdn, rendererType,
                rendererUrl, rendererManufacturer, rendererModel);

        try {
            TransportState state = renderer.getTransportState();
            rendererState.setText(state.value());
            rendererState.setForeground(stateColor(state));

            var pos = renderer.getPosition();
            rendererTrack.setText(pos.trackUri() != null ? pos.trackUri() : "None");
            rendererPosition.setText(
                    ContentItem.formatDuration(pos.relTime()) + " / "
                            + ContentItem.formatDuration(pos.trackDuration()));

            rendererVolume.setText(renderer.getVolume() + (renderer.getMute() ? " (Muted)" : ""));
        } catch (Exception e) {
            rendererState.setText("Error: " + e.getMessage());
            rendererState.setForeground(DarkTheme.ERROR);
            rendererTrack.setText("N/A");
            rendererPosition.setText("N/A");
            rendererVolume.setText("N/A");
        }
    }

    private void populateDeviceCard(DeviceProxy device) {
        populateBaseInfo(device, deviceName, deviceUdn, deviceType,
                deviceUrl, deviceManufacturer, deviceModel);

        servicesTableModel.setRowCount(0);
        var desc = parseDescription(device);
        if (desc != null) {
            for (var svc : desc.services()) {
                servicesTableModel.addRow(new Object[]{svc.serviceType(), svc.serviceId()});
            }
        }
    }

    private void populateFailedDeviceCard(ControlPoint.FailedDevice fd) {
        failedUdn.setText(fd.udn());
        failedLocation.setText(fd.location() != null ? fd.location() : "N/A");
        failedTimestamp.setText(Instant.ofEpochMilli(fd.timestamp())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        failedErrorText.setText(fd.errorMessage() != null ? fd.errorMessage() : "Unknown error");
        failedErrorText.setCaretPosition(0);
        failedResponseText.setText(fd.responseText() != null ? fd.responseText() : "(no response body)");
        failedResponseText.setCaretPosition(0);
    }

    private void populateBaseInfo(DeviceProxy device, JLabel nameLabel, JLabel udnLabel,
                                   JLabel typeLabel, JLabel urlLabel,
                                   JLabel mfgLabel, JLabel modelLabel) {
        nameLabel.setText(device.getFriendlyName());
        udnLabel.setText(device.getUdn());
        typeLabel.setText(device.getDeviceType());
        urlLabel.setText(device.getBaseUrl() != null ? device.getBaseUrl().toString() : "N/A");

        var desc = parseDescription(device);
        if (desc != null) {
            mfgLabel.setText(desc.manufacturer() != null ? desc.manufacturer() : "N/A");
            String model = desc.modelName() != null ? desc.modelName() : "N/A";
            if (desc.modelNumber() != null) {
                model += " (" + desc.modelNumber() + ")";
            }
            modelLabel.setText(model);
        } else {
            mfgLabel.setText("N/A");
            modelLabel.setText("N/A");
        }
    }

    private static Color stateColor(TransportState state) {
        return switch (state.value()) {
            case "PLAYING" -> DarkTheme.SUCCESS;
            case "PAUSED_PLAYBACK" -> DarkTheme.WARNING;
            case "STOPPED" -> DarkTheme.MUTED_TEXT;
            case "TRANSITIONING" -> DarkTheme.WARNING;
            default -> DarkTheme.MUTED_TEXT;
        };
    }

    private static DeviceDescription parseDescription(DeviceProxy device) {
        try {
            String xml = device.getDescriptionXml();
            if (xml != null && !xml.isEmpty()) {
                return DeviceDescription.parseXml(xml);
            }
        } catch (Exception e) {
            // Ignore parse failures
        }
        return null;
    }
}
