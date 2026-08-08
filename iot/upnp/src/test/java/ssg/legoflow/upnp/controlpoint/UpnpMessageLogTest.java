package ssg.legoflow.upnp.controlpoint;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for UpnpMessageLog covering message capture, filtering, and listeners.
 */
class UpnpMessageLogTest {

    @Test void testDisabledByDefault() {
        var log = new UpnpMessageLog();
        assertThat(log.isEnabled()).isFalse();
    }

    @Test void testEnableDisable() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        assertThat(log.isEnabled()).isTrue();
        log.setEnabled(false);
        assertThat(log.isEnabled()).isFalse();
    }

    @Test void testAddOutgoingWhenEnabled() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        log.logOutgoing("SSDP", "M-SEARCH *", "ssdp:all");
        assertThat(log.getEntries()).isNotEmpty();
    }

    @Test void testAddOutgoingWhenDisabledDoesNotCapture() {
        var log = new UpnpMessageLog();
        // Disabled by default
        log.logOutgoing("SSDP", "M-SEARCH", "ssdp:all");
        assertThat(log.getEntries()).isEmpty();
    }

    @Test void testIncomingSoap() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        log.logIncoming("SOAP", "Browse", "<?xml version=\"1.0\"?><root/>");
        var entries = log.getEntries();
        assertThat(entries).hasSize(1);
        var entry = entries.get(0);
        assertThat(entry.direction()).isEqualTo("<<<");
        assertThat(entry.protocol()).isEqualTo("SOAP");
    }

    @Test void testOutgoingSoap() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        log.logOutgoing("SOAP", "SetVolume", "<volume>50</volume>");
        var entries = log.getEntries();
        assertThat(entries).hasSize(1);
        var entry = entries.get(0);
        assertThat(entry.direction()).isEqualTo(">>>");
    }

    @Test void testIncomingGenA() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        log.logIncoming("GENA", "Volume event", "50");
        assertThat(log.getEntries()).hasSize(1);
    }

    @Test void testOutgoingGenA() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        log.logOutgoing("GENA", "SUBSCRIBE", "");
        assertThat(log.getEntries()).hasSize(1);
    }

    @Test void testHttpDescription() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        log.logIncoming("HTTP", "Device description", "<root/>");
        assertThat(log.getEntries()).hasSize(1);
    }

    @Test void testClearEntries() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        log.logOutgoing("SSDP", "M-SEARCH *", "ssdp:all");
        log.clear();
        assertThat(log.getEntries()).isEmpty();
    }

    @Test void testEntryCount() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        log.logOutgoing("SSDP", "M-SEARCH *", "ssdp:all");
        log.logIncoming("SSDP", "NOTIFY", "uuid:test");
        assertThat(log.getEntries()).hasSize(2);
    }

    @Test void testListenerNotification() throws Exception {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        
        List<UpnpMessageLog.LogEntry> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        log.addListener(entry -> {
            received.add(entry);
            latch.countDown();
        });
        
        log.logOutgoing("SSDP", "M-SEARCH *", "ssdp:all");
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
    }

    @Test void testListenerNotCalledWhenDisabled() throws Exception {
        var log = new UpnpMessageLog();
        // Disabled by default
        
        List<UpnpMessageLog.LogEntry> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        log.addListener(entry -> {
            received.add(entry);
            latch.countDown();
        });
        
        log.logOutgoing("SSDP", "M-SEARCH *", "ssdp:all");
        // Should not trigger listener
        assertThat(latch.await(200, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test void testLogEntryFormat() {
        var entry = new UpnpMessageLog.LogEntry(
            Instant.now(), ">>>", "SSDP", "M-SEARCH *", null);
        var formatted = entry.format();
        assertThat(formatted).contains(">>>");
        assertThat(formatted).contains("SSDP");
        assertThat(formatted).contains("M-SEARCH");
    }

    @Test void testLogEntryFormatWithBody() {
        var entry = new UpnpMessageLog.LogEntry(
            Instant.now(), "<<<", "SOAP", "Browse", "<root/>");
        var formatted = entry.format();
        assertThat(formatted).contains("<root/>");
    }

    @Test void testRemoveListener() {
        var log = new UpnpMessageLog();
        java.util.function.Consumer<UpnpMessageLog.LogEntry> listener = e -> {};
        log.addListener(listener);
        log.removeListener(listener);
        // Should not throw
    }

    @Test void testMaxEntriesLimit() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        for (int i = 0; i < 2500; i++) {
            log.logOutgoing("SSDP", "M-SEARCH", "test-" + i);
        }
        assertThat(log.getEntries().size()).isLessThanOrEqualTo(2100);
    }

    @Test void testExtractUdnFromUsn() {
        String udn = ControlPoint.extractUdn("uuid:device-123::urn:schemas-upnp-org:device:MediaServer:1");
        assertThat(udn).isEqualTo("uuid:device-123");
    }

    @Test void testExtractUdnSimple() {
        String udn = ControlPoint.extractUdn("uuid:simple-device");
        assertThat(udn).isEqualTo("uuid:simple-device");
    }

    @Test void testExtractUdnNullReturnsNull() {
        assertThat(ControlPoint.extractUdn(null)).isNull();
    }

    @Test void testExtractUdnEmptyReturnsNull() {
        assertThat(ControlPoint.extractUdn("")).isNull();
    }
    
    @Test void testExportAll() {
        var log = new UpnpMessageLog();
        log.setEnabled(true);
        log.logOutgoing("SSDP", "M-SEARCH *", null);
        String exported = log.exportAll();
        assertThat(exported).contains(">>>");
        assertThat(exported).contains("SSDP");
    }
}
