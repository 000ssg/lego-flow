package ssg.legoflow.upnp.controlpoint;

import org.junit.jupiter.api.Test;
import ssg.legoflow.upnp.mediarenderer.MediaRendererDevice;
import ssg.legoflow.upnp.mediaserver.ContentItem;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Extended ControlPoint coverage: device listeners, caching, removeDevice,
 * local-only mode, extractUdn utility, FailedDevice record.
 */
class ControlPointExtendedTest {

    // ── extractUdn utility ───────────────────────────────────────

    @Test void extractUdn_simple() {
        String udn = ControlPoint.extractUdn("uuid:device-001");
        assertThat(udn).isEqualTo("uuid:device-001");
    }

    @Test void extractUdn_withServiceSuffix() {
        String udn = ControlPoint.extractUdn(
                "uuid:device-001::urn:schemas-upnp-org:service:ContentDirectory:1");
        assertThat(udn).isEqualTo("uuid:device-001");
    }

    @Test void extractUdn_nullReturnsNull() {
        assertThat(ControlPoint.extractUdn(null)).isNull();
    }

    @Test void extractUdn_emptyReturnsNull() {
        assertThat(ControlPoint.extractUdn("")).isNull();
    }

    // ── Listener management ──────────────────────────────────────

    @Test void addRemoveDeviceListener() throws Exception {
        DeviceListener listener = new DeviceListener() {
            @Override public void onDeviceAdded(DeviceProxy d) {}
            @Override public void onDeviceRemoved(DeviceProxy d) {}
        };
        try (var cp = new ControlPoint()) {
            cp.addDeviceListener(listener);
            cp.removeDeviceListener(listener);
        }
    }

    @Test void addNullListenerThrows() throws Exception {
        try (var cp = new ControlPoint()) {
            assertThatThrownBy(() -> cp.addDeviceListener(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── Local device registration ────────────────────────────────

    @Test void registerLocalServer() throws Exception {
        var server = new MediaServerDevice("Test Server");
        try (var cp = new ControlPoint()) {
            cp.registerLocalServer(server);
            var servers = cp.discoverMediaServers();
            assertThat(servers).isNotEmpty();
            assertThat(servers.getFirst().getFriendlyName()).isEqualTo("Test Server");
        }
    }

    @Test void registerLocalRenderer() throws Exception {
        var renderer = new MediaRendererDevice("Test Renderer");
        try (var cp = new ControlPoint()) {
            cp.registerLocalRenderer(renderer);
            var renderers = cp.discoverMediaRenderers();
            assertThat(renderers).isNotEmpty();
            assertThat(renderers.getFirst().getFriendlyName()).isEqualTo("Test Renderer");
        }
    }

    // ── Device listing ───────────────────────────────────────────

    @Test void getDevicesEmptyInitially() throws Exception {
        try (var cp = new ControlPoint()) {
            var devices = cp.getDevices();
            assertThat(devices).isEmpty();
        }
    }

    @Test void getDevicesPopulatedAfterRegistration() throws Exception {
        var server = new MediaServerDevice("Server1");
        try (var cp = new ControlPoint()) {
            cp.registerLocalServer(server);
            var devices = cp.getDevices();
            assertThat(devices).isNotEmpty();
        }
    }

    // ── Device removal and notification ──────────────────────────

    @Test void removeDeviceNotifiesListener() throws Exception {
        var removedRef = new AtomicReference<DeviceProxy>();
        DeviceListener devListener = new DeviceListener() {
            @Override public void onDeviceAdded(DeviceProxy d) {}
            @Override public void onDeviceRemoved(DeviceProxy d) {
                removedRef.set(d);
            }
        };

        var server = new MediaServerDevice("Server1");
        String udn = server.getUdn();
        try (var cp = new ControlPoint()) {
            cp.addDeviceListener(devListener);
            cp.registerLocalServer(server);
            cp.removeDevice(udn);
            assertThat(removedRef.get()).isNotNull();
            assertThat(removedRef.get().getFriendlyName()).isEqualTo("Server1");
        }
    }

    @Test void removeNonexistentDeviceDoesNotNotify() throws Exception {
        var removed = new AtomicBoolean(false);
        DeviceListener devListener = new DeviceListener() {
            @Override public void onDeviceAdded(DeviceProxy d) {}
            @Override public void onDeviceRemoved(DeviceProxy d) {
                removed.set(true);
            }
        };

        try (var cp = new ControlPoint()) {
            cp.addDeviceListener(devListener);
            cp.removeDevice("uuid:nonexistent");
            assertThat(removed.get()).isFalse();
        }
    }

    // ── Start/stop lifecycle ────────────────────────────────────

    @Test void startLocalOnlyModeDoesNotThrow() throws Exception {
        try (var cp = new ControlPoint()) {
            cp.start();  // no network interface -> local-only
            assertThat(cp.isRunning()).isTrue();
        }
    }

    @Test void doubleStartIsSafe() throws Exception {
        try (var cp = new ControlPoint()) {
            cp.start();
            cp.start();  // second start should be a no-op
        }
    }

    @Test void stopAfterStart() throws Exception {
        try (var cp = new ControlPoint()) {
            cp.start();
            cp.stop();
            assertThat(cp.isRunning()).isFalse();
        }
    }

    // ── Failed device cache ──────────────────────────────────────

    @Test void getFailedDevicesInitiallyEmpty() throws Exception {
        try (var cp = new ControlPoint()) {
            var failed = cp.getFailedDevices();
            assertThat(failed).isEmpty();
        }
    }

    @Test void failedDeviceRecordFields() {
        var fd = new ControlPoint.FailedDevice(
                "uuid:fail", "http://1.2.3.4/desc.xml", "timeout", null, System.currentTimeMillis());
        assertThat(fd.udn()).isEqualTo("uuid:fail");
        assertThat(fd.location()).isEqualTo("http://1.2.3.4/desc.xml");
        assertThat(fd.errorMessage()).isEqualTo("timeout");
    }

    // ── isRunning and lifecycle state ────────────────────────────

    @Test void isRunningFalseInitially() throws Exception {
        try (var cp = new ControlPoint()) {
            assertThat(cp.isRunning()).isFalse();
        }
    }

    // ── Message log access ───────────────────────────────────────

    @Test void getMessageLogReturnsNonNull() throws Exception {
        try (var cp = new ControlPoint()) {
            var log = cp.getMessageLog();
            assertThat(log).isNotNull();
        }
    }
}
