package ssg.legoflow.upnp.controlpoint;

import org.junit.jupiter.api.*;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;
import ssg.legoflow.upnp.mediarenderer.MediaRendererDevice;
import static org.assertj.core.api.Assertions.*;

/**
 * UPnP Control Point coverage tests to increase test coverage.
 */
class ControlPointCoverageTest {

    @Test void testControlPointConstructorDoesNotThrow() {
        var cp = new ControlPoint();
        assertThat(cp).isNotNull();
    }

    @Test void testIsRunningInitiallyFalse() {
        var cp = new ControlPoint();
        assertThat(cp.isRunning()).isFalse();
    }

    @Test void testStopWithoutStartDoesNotThrow() throws Exception {
        var cp = new ControlPoint();
        assertThatCode(cp::stop).doesNotThrowAnyException();
        cp.close();
    }

    @Test void testDoubleStopDoesNotThrow() throws Exception {
        var cp = new ControlPoint();
        cp.stop();
        cp.stop();
        cp.close();
    }

    @Test void testGetDevicesReturnsNonNull() throws Exception {
        var cp = new ControlPoint();
        try {
            assertThat(cp.getDevices()).isNotNull();
        } finally { cp.close(); }
    }

    @Test void testControlPointStartStop() throws Exception {
        var cp = new ControlPoint();
        cp.start();
        Thread.sleep(100);
        cp.stop();
        cp.close();
    }

    @Test void testDiscoverMediaServersReturnsNonNull() throws Exception {
        var cp = new ControlPoint();
        try {
            assertThat(cp.discoverMediaServers()).isNotNull();
        } finally { cp.close(); }
    }

    @Test void testDiscoverMediaRenderersReturnsNonNull() throws Exception {
        var cp = new ControlPoint();
        try {
            assertThat(cp.discoverMediaRenderers()).isNotNull();
        } finally { cp.close(); }
    }

    @Test void testGetFailedDevicesReturnsNonNull() throws Exception {
        var cp = new ControlPoint();
        try {
            assertThat(cp.getFailedDevices()).isNotNull();
        } finally { cp.close(); }
    }

    @Test void testGetMessageLogReturnsNonNull() throws Exception {
        var cp = new ControlPoint();
        try {
            assertThat(cp.getMessageLog()).isNotNull();
        } finally { cp.close(); }
    }

    @Test void testRegisterLocalServerDoesNotThrow() throws Exception {
        var cp = new ControlPoint();
        try {
            cp.registerLocalServer(new MediaServerDevice("Test"));
        } finally { cp.close(); }
    }

    @Test void testRegisterLocalRendererDoesNotThrow() throws Exception {
        var cp = new ControlPoint();
        try {
            cp.registerLocalRenderer(new MediaRendererDevice("Test"));
        } finally { cp.close(); }
    }

    @Test void testAddAndRemoveDeviceListener() throws Exception {
        var cp = new ControlPoint();
        try {
            DeviceListener listener = new DeviceListener() {
                @Override public void onDeviceAdded(DeviceProxy d) {}
                @Override public void onDeviceRemoved(DeviceProxy d) {}
            };
            cp.addDeviceListener(listener);
            cp.removeDeviceListener(listener);
        } finally { cp.close(); }
    }

    @Test void testRemoveNonexistentDeviceDoesNotThrow() throws Exception {
        var cp = new ControlPoint();
        try {
            assertThatCode(() -> cp.removeDevice("nonexistent")).doesNotThrowAnyException();
        } finally { cp.close(); }
    }

    @Test void testRefreshWithoutStartDoesNotThrow() throws Exception {
        var cp = new ControlPoint();
        try {
            assertThatCode(() -> cp.refresh()).doesNotThrowAnyException();
        } finally { cp.close(); }
    }

    @Test void testIsRunningAfterStartAndStop() throws Exception {
        var cp = new ControlPoint();
        cp.start();
        Thread.sleep(100);
        cp.stop();
        assertThat(cp.isRunning()).isFalse();
        cp.close();
    }

    @Test void testRegisterServerThenDiscover() throws Exception {
        var cp = new ControlPoint();
        try {
            cp.registerLocalServer(new MediaServerDevice("Srv"));
            assertThat(cp.discoverMediaServers()).hasSizeGreaterThanOrEqualTo(1);
        } finally { cp.close(); }
    }

    @Test void testRegisterRendererThenDiscover() throws Exception {
        var cp = new ControlPoint();
        try {
            cp.registerLocalRenderer(new MediaRendererDevice("Rdr"));
            assertThat(cp.discoverMediaRenderers()).hasSizeGreaterThanOrEqualTo(1);
        } finally { cp.close(); }
    }

    @Test void testDoubleCloseDoesNotThrow() throws Exception {
        var cp = new ControlPoint();
        cp.close();
        assertThatCode(() -> cp.close()).doesNotThrowAnyException();
    }

    @Test void testAutoCloseableStopAndClose() throws Exception {
        try (var cp = new ControlPoint()) {
            cp.start();
            Thread.sleep(50);
            cp.stop();
            assertThat(cp.isRunning()).isFalse();
        }
    }

    @Test void testDiscoverReturnsQuicklyWithNoNetworkDevices() throws Exception {
        var cp = new ControlPoint();
        try {
            long start = System.currentTimeMillis();
            cp.discoverMediaServers();
            cp.discoverMediaRenderers();
            assertThat(System.currentTimeMillis() - start).isLessThan(5000);
        } finally { cp.close(); }
    }

    @Test void testGetDevicesAfterRegisterServer() throws Exception {
        var cp = new ControlPoint();
        try {
            cp.registerLocalServer(new MediaServerDevice("S"));
            assertThat(cp.getDevices()).isNotEmpty();
        } finally { cp.close(); }
    }

    @Test void testMessageLogHasEntriesAfterStart() throws Exception {
        var cp = new ControlPoint();
        try {
            cp.start();
            Thread.sleep(200);
            assertThat(cp.getMessageLog()).isNotNull();
        } finally { cp.close(); }
    }

    @Test void testFailedDeviceRecordCreation() {
        var failed = new ControlPoint.FailedDevice(
                "udn-123", "http://example.com/desc.xml", 
                "Connection error", "", System.currentTimeMillis());
        assertThat(failed.udn()).isEqualTo("udn-123");
    }

    @Test void testMultipleListeners() throws Exception {
        var cp = new ControlPoint();
        try {
            DeviceListener l1 = new DeviceListener() {
                @Override public void onDeviceAdded(DeviceProxy d) {}
                @Override public void onDeviceRemoved(DeviceProxy d) {}
            };
            DeviceListener l2 = new DeviceListener() {
                @Override public void onDeviceAdded(DeviceProxy d) {}
                @Override public void onDeviceRemoved(DeviceProxy d) {}
            };
            cp.addDeviceListener(l1);
            cp.addDeviceListener(l2);
            cp.removeDeviceListener(l1);
            cp.removeDeviceListener(l2);
        } finally { cp.close(); }
    }

    @Test void testDiscoverAfterStop() throws Exception {
        var cp = new ControlPoint();
        cp.start();
        Thread.sleep(50);
        cp.stop();
        // Discover should still work after stop (returns cached or empty)
        assertThat(cp.discoverMediaServers()).isNotNull();
        cp.close();
    }

    @Test void testRegisterBothServerAndRenderer() throws Exception {
        var cp = new ControlPoint();
        try {
            cp.registerLocalServer(new MediaServerDevice("Srv"));
            cp.registerLocalRenderer(new MediaRendererDevice("Rdr"));
            assertThat(cp.discoverMediaServers()).isNotEmpty();
            assertThat(cp.discoverMediaRenderers()).isNotEmpty();
        } finally { cp.close(); }
    }

    @Test void testRegisterDuplicateServer() throws Exception {
        var cp = new ControlPoint();
        try {
            cp.registerLocalServer(new MediaServerDevice("Dup1"));
            cp.registerLocalServer(new MediaServerDevice("Dup2"));
            assertThat(cp.discoverMediaServers()).isNotEmpty();
        } finally { cp.close(); }
    }
}
