package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.DeviceListener;
import ssg.legoflow.upnp.controlpoint.DeviceProxy;
import javax.swing.*;
import java.awt.*;
/**
 * Bottom status bar showing discovery state and selected devices.
 *
 * <p>Displays the number of discovered devices, the currently selected
 * media server and renderer names, and the control point connection status.
 * Auto-updates when devices are added or removed. Uses the {@link DarkTheme}
 * colour palette for all visual elements.
 *
 * @since 0.1.0
 */
public class StatusBar extends JPanel implements DeviceListener {

    private final ControlPoint controlPoint;
    private final JLabel deviceCountLabel;
    private final JLabel serverLabel;
    private final JLabel rendererLabel;
    private final JLabel mediaSupportLabel;
    private final JLabel statusLabel;

    private volatile String currentServerName = "None";
    private volatile String currentRendererName = "None";

    /**
     * Creates a new status bar bound to the given control point.
     *
     * @param controlPoint the control point to monitor
     * @since 0.1.0
     */
    public StatusBar(ControlPoint controlPoint) {
        this.controlPoint = controlPoint;
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBackground(DarkTheme.PANEL_BG);
        setOpaque(true);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        deviceCountLabel = new JLabel("Devices: 0");
        deviceCountLabel.setForeground(DarkTheme.TEXT);

        serverLabel = new JLabel("Server: None");
        serverLabel.setForeground(DarkTheme.TEXT);

        rendererLabel = new JLabel("Renderer: None");
        rendererLabel.setForeground(DarkTheme.TEXT);

        mediaSupportLabel = new JLabel("");
        mediaSupportLabel.setForeground(DarkTheme.ACCENT);
        mediaSupportLabel.setFont(mediaSupportLabel.getFont().deriveFont(Font.ITALIC, 11f));

        statusLabel = new JLabel("Connected");
        statusLabel.setForeground(DarkTheme.SUCCESS);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 11f));

        var sep1 = new JSeparator(SwingConstants.VERTICAL);
        sep1.setForeground(DarkTheme.BORDER);
        sep1.setBackground(DarkTheme.PANEL_BG);

        var sep2 = new JSeparator(SwingConstants.VERTICAL);
        sep2.setForeground(DarkTheme.BORDER);
        sep2.setBackground(DarkTheme.PANEL_BG);

        var sep3 = new JSeparator(SwingConstants.VERTICAL);
        sep3.setForeground(DarkTheme.BORDER);
        sep3.setBackground(DarkTheme.PANEL_BG);

        add(deviceCountLabel);
        add(Box.createHorizontalStrut(16));
        add(sep1);
        add(Box.createHorizontalStrut(8));
        add(serverLabel);
        add(Box.createHorizontalStrut(16));
        add(sep2);
        add(Box.createHorizontalStrut(8));
        add(rendererLabel);
        add(Box.createHorizontalStrut(16));
        add(sep3);
        add(Box.createHorizontalStrut(8));
        add(mediaSupportLabel);
        add(Box.createHorizontalGlue());
        add(statusLabel);

        controlPoint.addDeviceListener(this);
        updateDeviceCount();
    }

    /**
     * Shows a transient status message in the status bar.
     *
     * @param message the status message to display
     * @since 0.1.0
     */
    public void setStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            statusLabel.setForeground(DarkTheme.TEXT);
        });
    }

    /**
     * Sets the name of the currently selected media server.
     *
     * @param name the server friendly name, or {@code null} for none
     * @since 0.1.0
     */
    public void setCurrentServer(String name) {
        currentServerName = name != null ? name : "None";
        SwingUtilities.invokeLater(() -> serverLabel.setText("Server: " + currentServerName));
    }

    /**
     * Sets the name of the currently selected media renderer.
     *
     * @param name the renderer friendly name, or {@code null} for none
     * @since 0.1.0
     */
    public void setCurrentRenderer(String name) {
        currentRendererName = name != null ? name : "None";
        SwingUtilities.invokeLater(() -> rendererLabel.setText("Renderer: " + currentRendererName));
    }

    /**
     * Sets the media support info text shown in the status bar.
     *
     * <p>Displays which library/framework provides support for the currently
     * playing media type, or a "not supported" message.
     *
     * @param info the media support info string, or {@code null} to clear
     * @since 0.1.0
     */
    public void setMediaSupportInfo(String info) {
        SwingUtilities.invokeLater(() -> {
            if (info != null && !info.isEmpty()) {
                mediaSupportLabel.setText("Media: " + info);
            } else {
                mediaSupportLabel.setText("");
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.1.0
     */
    @Override
    public void onDeviceAdded(DeviceProxy device) {
        updateDeviceCount();
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.1.0
     */
    @Override
    public void onDeviceRemoved(DeviceProxy device) {
        updateDeviceCount();
    }

    private void updateDeviceCount() {
        int count = controlPoint.getDevices().size();
        SwingUtilities.invokeLater(() -> {
            deviceCountLabel.setText("Devices: " + count);
            if (controlPoint.isRunning()) {
                statusLabel.setText("Connected");
                statusLabel.setForeground(DarkTheme.SUCCESS);
            } else {
                statusLabel.setText("Disconnected");
                statusLabel.setForeground(DarkTheme.ERROR);
            }
        });
    }
}
