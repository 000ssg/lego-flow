package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.DeviceProxy;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.device.DeviceDescription;
import ssg.legoflow.upnp.device.ServiceDescription;
import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import ssg.legoflow.upnp.mediarenderer.TransportState;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
/**
 * Dialog displaying detailed information about a UPnP device.
 *
 * <p>Shows the device identification fields (friendly name, UDN, manufacturer,
 * model), lists all hosted services, and for media servers shows protocol info
 * while for media renderers shows the current transport state. All visual
 * elements use the {@link DarkTheme} colour palette.
 *
 * @since 0.1.0
 */
public class DevicePropertiesDialog extends JDialog {

    /**
     * Creates and shows a device properties dialog.
     *
     * @param parent the parent frame
     * @param device the device to inspect
     * @since 0.1.0
     */
    public DevicePropertiesDialog(Frame parent, DeviceProxy device) {
        super(parent, "Device Properties — " + device.getFriendlyName(), true);
        setSize(500, 450);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(DarkTheme.PANEL_BG);

        var tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(DarkTheme.PANEL_BG);
        tabbedPane.setForeground(DarkTheme.TEXT);

        tabbedPane.addTab("General", createGeneralPanel(device));
        tabbedPane.addTab("Services", createServicesPanel(device));

        if (device instanceof MediaServerProxy serverProxy) {
            tabbedPane.addTab("Protocol Info", createProtocolInfoPanel(serverProxy));
        }
        if (device instanceof MediaRendererProxy rendererProxy) {
            tabbedPane.addTab("Transport", createTransportPanel(rendererProxy));
        }

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        var closeButton = new JButton("Close");
        closeButton.setBackground(DarkTheme.PANEL_BG);
        closeButton.setForeground(DarkTheme.TEXT);
        closeButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> dispose());

        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(DarkTheme.PANEL_BG);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createGeneralPanel(DeviceProxy device) {
        var panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addField(panel, gbc, row++, "Friendly Name:", device.getFriendlyName());
        addField(panel, gbc, row++, "UDN:", device.getUdn());
        addField(panel, gbc, row++, "Device Type:", device.getDeviceType());

        DeviceDescription desc = parseDescription(device);
        if (desc != null) {
            addField(panel, gbc, row++, "Manufacturer:", desc.manufacturer());
            addField(panel, gbc, row++, "Model Name:", desc.modelName());
            addField(panel, gbc, row++, "Model Number:", desc.modelNumber() != null ? desc.modelNumber() : "N/A");
            addField(panel, gbc, row++, "Serial Number:", desc.serialNumber() != null ? desc.serialNumber() : "N/A");
        }

        addField(panel, gbc, row, "Base URL:", device.getBaseUrl().toString());

        gbc.gridy = row + 1;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private JPanel createServicesPanel(DeviceProxy device) {
        var panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(DarkTheme.PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        var tableModel = new DefaultTableModel(
                new String[]{"Service Type", "Service ID"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        DeviceDescription desc = parseDescription(device);
        if (desc != null) {
            for (ServiceDescription svc : desc.services()) {
                tableModel.addRow(new Object[]{svc.serviceType(), svc.serviceId()});
            }
        }

        var table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        DarkTheme.styleTable(table);

        var scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(DarkTheme.BODY_BG);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createProtocolInfoPanel(MediaServerProxy server) {
        var panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(DarkTheme.PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        var tableModel = new DefaultTableModel(
                new String[]{"Protocol", "Network", "Content Format", "Additional Info"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        try {
            List<DlnaProtocolInfo> protocols = server.getProtocolInfo();
            for (DlnaProtocolInfo pi : protocols) {
                tableModel.addRow(new Object[]{
                        pi.protocol(), pi.network(), pi.contentFormat(), pi.additionalInfo()
                });
            }
        } catch (Exception e) {
            tableModel.addRow(new Object[]{"Error loading protocol info", "", "", e.getMessage()});
        }

        var table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        DarkTheme.styleTable(table);

        var scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(DarkTheme.BODY_BG);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createTransportPanel(MediaRendererProxy renderer) {
        var panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        try {
            TransportState state = renderer.getTransportState();
            addField(panel, gbc, row++, "Transport State:", state.value());

            var posInfo = renderer.getPosition();
            addField(panel, gbc, row++, "Track:", String.valueOf(posInfo.track()));
            addField(panel, gbc, row++, "Track URI:", posInfo.trackUri());
            addField(panel, gbc, row++, "Position:",
                    ssg.legoflow.upnp.mediaserver.ContentItem.formatDuration(posInfo.relTime()));
            addField(panel, gbc, row++, "Duration:",
                    ssg.legoflow.upnp.mediaserver.ContentItem.formatDuration(posInfo.trackDuration()));

            addField(panel, gbc, row++, "Volume:", String.valueOf(renderer.getVolume()));
            addField(panel, gbc, row, "Muted:", String.valueOf(renderer.getMute()));
        } catch (Exception e) {
            addField(panel, gbc, row, "Error:", e.getMessage());
        }

        gbc.gridy = row + 1;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private void addField(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        var labelComp = new JLabel(label);
        labelComp.setFont(labelComp.getFont().deriveFont(Font.BOLD));
        labelComp.setForeground(DarkTheme.SECONDARY_TEXT);
        panel.add(labelComp, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        var valueComp = new JLabel(value != null ? value : "N/A");
        valueComp.setForeground(DarkTheme.TEXT);
        panel.add(valueComp, gbc);
    }

    private DeviceDescription parseDescription(DeviceProxy device) {
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
