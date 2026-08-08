package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.demo.SimpleMediaRendererDemo;
import ssg.legoflow.upnp.demo.SimpleMediaServerDemo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Application launcher for the Media Control Center web demo.
 *
 * <p>Creates demo media server and renderer devices with sample content,
 * registers them with a control point, starts the web server, and waits
 * for user input to stop. The React SPA is accessible at the URL
 * printed on startup (default port 8080, bound to all interfaces).
 *
 * @since 0.1.0
 */
public class MccWebApp {

    private final MccWebServer webServer;
    private final ControlPoint controlPoint;
    private final SimpleMediaServerDemo serverDemo;
    private final SimpleMediaRendererDemo rendererDemo;

    /**
     * Creates and initializes the web application.
     *
     * @since 0.1.0
     */
    public MccWebApp() {
        this(8080);
    }

    /**
     * Creates and initializes the web application on the specified port.
     *
     * @param port the HTTP port
     * @since 0.1.0
     */
    public MccWebApp(int port) {
        // Create demo devices
        serverDemo = new SimpleMediaServerDemo();
        rendererDemo = new SimpleMediaRendererDemo();

        // Create control point with all physical network interfaces for SSDP scanning
        var ifaces = findAllPhysicalInterfaces();
        controlPoint = ifaces.isEmpty() ? new ControlPoint() : new ControlPoint(ifaces);
        controlPoint.start();
        controlPoint.registerLocalServer(serverDemo.getServer());
        controlPoint.registerLocalRenderer(rendererDemo.getRenderer());

        // Start devices
        serverDemo.start();
        rendererDemo.start();

        // Create web server
        webServer = new MccWebServer(port, controlPoint);
    }

    /**
     * Starts the web application.
     *
     * @since 0.1.0
     */
    public void start() {
        webServer.start();
    }

    /**
     * Stops the web application and all associated devices.
     *
     * @since 0.1.0
     */
    public void stop() {
        webServer.stop();
        rendererDemo.stop();
        serverDemo.stop();
        controlPoint.stop();
    }

    /**
     * Returns the web server.
     *
     * @return the web server
     * @since 0.1.0
     */
    public MccWebServer getWebServer() {
        return webServer;
    }

    /**
     * Returns the control point.
     *
     * @return the control point
     * @since 0.1.0
     */
    public ControlPoint getControlPoint() {
        return controlPoint;
    }

    /**
     * Resolves the host address to display in the startup URL.
     *
     * <p>Prefers physical LAN interface addresses over VPN/tunnel addresses.
     * Falls back to {@code localhost} if no suitable address is found.
     *
     * @return the host address string
     */
    private static String resolveHostAddress() {
        var ifaces = findAllPhysicalInterfaces();
        for (var iface : ifaces) {
            var addrs = iface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                var addr = addrs.nextElement();
                if (addr instanceof java.net.Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        }
        return "localhost";
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

    /**
     * Main entry point for running the Media Control Center web demo.
     *
     * <p>Starts the server and waits for the user to press Enter to stop.
     *
     * @param args command-line arguments (unused)
     * @since 0.1.0
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        var app = new MccWebApp(port);
        app.start();
        String hostAddress = resolveHostAddress();
        System.out.println("Media Control Center running at http://" + hostAddress + ":" + port);
        System.out.println("Press Enter to stop...");
        try {
            new BufferedReader(new InputStreamReader(System.in)).readLine();
        } catch (Exception e) {
            // Interrupted
        }
        app.stop();
        System.out.println("Media Control Center stopped.");
    }
}
