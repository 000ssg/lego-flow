package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.dlna.DlnaMediaFormat;
import ssg.legoflow.upnp.mediarenderer.MediaRendererDevice;
import ssg.legoflow.upnp.mediaserver.ContentContainer;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.ContentItemType;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Application launcher for the Media Control Center demo.
 *
 * <p>Sets the system Look and Feel, creates in-process demo devices
 * (a {@link MediaServerDevice} with sample content and a
 * {@link MediaRendererDevice}), registers them with a {@link ControlPoint},
 * and launches the {@link MediaControlCenter} Swing application.
 *
 * <p>If the platform supports a system tray, a tray icon is installed
 * allowing the application to be minimized to the tray.
 *
 * @since 1.0.0
 */
public class MediaControlCenterApp {

    private MediaControlCenter mcc;
    private ControlPoint controlPoint;
    private MediaServerDevice serverDevice;
    private MediaRendererDevice rendererDevice;

    /**
     * Creates a new application instance.
     *
     * @since 1.0.0
     */
    public MediaControlCenterApp() {
    }

    /**
     * Initializes and launches the application.
     *
     * <p>Creates the control point and UI immediately (on the EDT), then
     * starts all network I/O (SSDP multicast, demo device HTTP servers,
     * device registration and discovery) in a background thread so the
     * window appears instantly without blocking.
     *
     * @since 1.0.0
     */
    public void launch() {
        var ifaces = findAllPhysicalInterfaces();
        controlPoint = ifaces.isEmpty() ? new ControlPoint() : new ControlPoint(ifaces);

        // Create and show UI immediately — no blocking I/O on the EDT
        mcc = new MediaControlCenter(controlPoint);
        mcc.setVisible(true);
        setupSystemTray();

        // All network I/O runs in background
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                controlPoint.start();

                serverDevice = createDemoServer();
                serverDevice.start();
                controlPoint.registerLocalServer(serverDevice);

                rendererDevice = createDemoRenderer();
                rendererDevice.start();
                controlPoint.registerLocalRenderer(rendererDevice);

                controlPoint.refresh();
                return null;
            }

            @Override
            protected void done() {
                mcc.getDeviceListPanel().refreshDeviceLists();
            }
        }.execute();
    }

    /**
     * Returns the Media Control Center frame.
     *
     * @return the MCC frame
     * @since 1.0.0
     */
    public MediaControlCenter getMcc() {
        return mcc;
    }

    /**
     * Returns the control point.
     *
     * @return the control point
     * @since 1.0.0
     */
    public ControlPoint getControlPoint() {
        return controlPoint;
    }

    /**
     * Returns the demo server device.
     *
     * @return the server device
     * @since 1.0.0
     */
    public MediaServerDevice getServerDevice() {
        return serverDevice;
    }

    /**
     * Returns the demo renderer device.
     *
     * @return the renderer device
     * @since 1.0.0
     */
    public MediaRendererDevice getRendererDevice() {
        return rendererDevice;
    }

    /**
     * Finds all physical network interfaces suitable for multicast SSDP.
     *
     * <p>Returns all up, non-loopback, multicast-capable, IPv4-bearing interfaces
     * whose names match physical LAN adapter patterns ({@code en*} on macOS,
     * {@code eth*}/{@code wlan*}/{@code enp*}/{@code ens*} on Linux).
     * VPN tunnel interfaces ({@code utun*}, {@code tun*}), Apple wireless direct
     * ({@code awdl*}, {@code llw*}), bridges, and VM adapters are excluded.
     *
     * <p>If no physical interface is found, non-physical up interfaces with IPv4
     * are included as a fallback so that at least one interface is tried.
     *
     * @return a list of suitable interfaces (may be empty)
     */
    private static List<NetworkInterface> findAllPhysicalInterfaces() {
        try {
            var physical = new ArrayList<NetworkInterface>();
            var fallbacks = new ArrayList<NetworkInterface>();
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                var iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || !iface.supportsMulticast()) {
                    continue;
                }
                boolean hasIpv4 = false;
                var addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    if (addrs.nextElement() instanceof java.net.Inet4Address) {
                        hasIpv4 = true;
                        break;
                    }
                }
                if (!hasIpv4) continue;

                if (isPhysicalLanInterface(iface.getName())) {
                    physical.add(iface);
                } else {
                    fallbacks.add(iface);
                }
            }
            return physical.isEmpty() ? fallbacks : physical;
        } catch (SocketException e) {
            return List.of();
        }
    }

    /**
     * Returns {@code true} if the interface name looks like a physical LAN adapter.
     *
     * <p>On macOS: {@code en0} (Wi-Fi), {@code en1}–{@code en9} (Ethernet/Thunderbolt).
     * On Linux: {@code eth*}, {@code wlan*}, {@code enp*}, {@code ens*}.
     * Excludes VPN tunnels ({@code utun*}, {@code tun*}, {@code tap*}),
     * Apple wireless direct ({@code awdl*}, {@code llw*}), bridges, and VM adapters.
     */
    private static boolean isPhysicalLanInterface(String name) {
        return name.startsWith("en") || name.startsWith("eth")
                || name.startsWith("wlan") || name.startsWith("enp")
                || name.startsWith("ens");
    }

    private MediaServerDevice createDemoServer() {
        var server = new MediaServerDevice("MCC Demo Media Server");
        server.setHttpPort(8200).setHostAddress("127.0.0.1");
        String baseUrl = server.getBaseUrl();

        // Music container
        var music = new ContentContainer("1", "0", "Music", true);
        server.addContainer(music);

        var album = new ContentContainer("10", "1", "Best of Demo", true);
        server.addContainer(album);

        var track1 = new ContentItem("100", "10", "Opening Theme", ContentItemType.AUDIO_ITEM);
        track1.setCreator("Demo Artist")
                .setGenre("Electronic")
                .setDate("2025-01-10")
                .setDuration(Duration.ofMinutes(3).plusSeconds(45))
                .setSize(5_200_000L)
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/track1.mp3"));
        server.addContent(track1);

        var track2 = new ContentItem("101", "10", "Midnight Drive", ContentItemType.AUDIO_ITEM);
        track2.setCreator("Demo Artist")
                .setGenre("Electronic")
                .setDate("2025-01-10")
                .setDuration(Duration.ofMinutes(4).plusSeconds(20))
                .setSize(6_100_000L)
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/track2.mp3"));
        server.addContent(track2);

        var track3 = new ContentItem("102", "10", "Sunset Boulevard", ContentItemType.AUDIO_ITEM);
        track3.setCreator("Demo Band")
                .setGenre("Rock")
                .setDate("2025-02-20")
                .setDuration(Duration.ofMinutes(5).plusSeconds(10))
                .setSize(7_300_000L)
                .setProtocolInfo(DlnaMediaFormat.MP3.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/track3.mp3"));
        server.addContent(track3);

        // Video container
        var video = new ContentContainer("2", "0", "Video", true);
        server.addContainer(video);

        var movie1 = new ContentItem("200", "2", "Demo Documentary", ContentItemType.VIDEO_ITEM);
        movie1.setCreator("Demo Director")
                .setGenre("Documentary")
                .setDate("2025-03-15")
                .setDuration(Duration.ofHours(1).plusMinutes(30))
                .setSize(1_800_000_000L)
                .setResolution("1920x1080")
                .setProtocolInfo(DlnaMediaFormat.AVC_MP4_MP_SD.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/movie1.mp4"));
        server.addContent(movie1);

        // Photos container
        var photos = new ContentContainer("3", "0", "Photos", true);
        server.addContainer(photos);

        var photo1 = new ContentItem("300", "3", "Mountain View", ContentItemType.IMAGE_ITEM);
        photo1.setCreator("Demo Photographer")
                .setDate("2025-04-01")
                .setSize(3_200_000L)
                .setResolution("4000x3000")
                .setProtocolInfo(DlnaMediaFormat.JPEG_LRG.toProtocolInfo())
                .setResourceUrl(createUrl(baseUrl + "/content/photo1.jpg"));
        server.addContent(photo1);

        return server;
    }

    private MediaRendererDevice createDemoRenderer() {
        var renderer = new MediaRendererDevice("MCC Demo Renderer");
        renderer.setHttpPort(8300).setHostAddress("127.0.0.1");
        return renderer;
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) return;

        try {
            var tray = SystemTray.getSystemTray();
            // Create a simple text-based icon (16x16)
            var image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            g.setColor(new Color(0, 120, 200));
            g.fillOval(1, 1, 14, 14);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.drawString("M", 3, 12);
            g.dispose();

            var popup = new PopupMenu("MCC");
            var showItem = new MenuItem("Show");
            showItem.addActionListener(e -> {
                mcc.setVisible(true);
                mcc.setState(Frame.NORMAL);
            });
            popup.add(showItem);

            var exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> mcc.shutdown());
            popup.add(exitItem);

            var trayIcon = new TrayIcon(image, "Media Control Center", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> {
                mcc.setVisible(true);
                mcc.setState(Frame.NORMAL);
            });

            tray.add(trayIcon);

            mcc.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowIconified(java.awt.event.WindowEvent e) {
                    mcc.setVisible(false);
                }
            });
        } catch (AWTException e) {
            // System tray not available
        }
    }

    private static URL createUrl(String urlString) {
        try {
            return URI.create(urlString).toURL();
        } catch (Exception e) {
            throw new RuntimeException("Invalid URL: " + urlString, e);
        }
    }

    /**
     * Application entry point.
     *
     * <p>Sets the system Look and Feel, then launches the Media Control Center
     * on the Event Dispatch Thread.
     *
     * @param args command-line arguments (unused)
     * @since 1.0.0
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default LAF
        }
        DarkTheme.apply();

        SwingUtilities.invokeLater(() -> {
            var app = new MediaControlCenterApp();
            app.launch();
        });
    }
}
