package ssg.legoflow.network.terminals.base.io;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.event.TerminalEvent;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for TerminalFactory — registry and creation of terminal instances.
 */
class TerminalFactoryTest {

    private int typeCounter = 0;

    private Terminal createDummy(String name) {
        TerminalConfig cfg = TerminalConfig.builder().build();
        return new Terminal() {
            @Override public String type() { return name; }
            @Override public void feed(byte[] data) {}
            @Override public void feed(String text) {}
            @Override public java.util.List<String> render() { return java.util.List.of(); }
            @Override public Cursor cursor() { return null; }
            @Override public TermAttr currentAttr() { return TermAttr.DEFAULT; }
            @Override public TerminalConfig config() { return cfg; }
            @Override public DisplayModel displayModel() { return null; }
            @Override public void addEventListener(TerminalEventListener l) {}
            @Override public void removeEventListener(TerminalEventListener l) {}
            @Override public void reset() {}
            @Override public String title() { return ""; }
            @Override public boolean supportsColor() { return false; }
        };
    }

    private String uniqueType() {
        return "test-" + (typeCounter++);
    }

    @Test
    void testRegisterAndLookup() {
        String type = uniqueType();
        Terminal terminal = createDummy(type);
        TerminalFactory.register(type, config -> terminal);
        var found = TerminalFactory.lookup(type);

        assertThat(found).isNotNull();
        Terminal created = found.create(TerminalConfig.builder().build());
        assertThat(created).isSameAs(terminal);
    }

    @Test
    void testLookupCaseInsensitive() {
        String type = uniqueType();
        TerminalFactory.register(type, config -> createDummy(type));

        assertThat(TerminalFactory.lookup(type.toUpperCase())).isNotNull();
        assertThat(TerminalFactory.lookup(type.toLowerCase())).isNotNull();
    }

    @Test
    void testLookupNullReturnsNull() {
        assertThat(TerminalFactory.lookup(null)).isNull();
    }

    @Test
    void testLookupUnregisteredReturnsNull() {
        assertThat(TerminalFactory.lookup("nonexistent-" + System.nanoTime())).isNull();
    }

    @Test
    void testCreateWithDefaultConfig() {
        String type = uniqueType();
        var capturedConfig = new TerminalConfig[1];
        TerminalFactory.register(type, config -> {
            capturedConfig[0] = config;
            return createDummy(type);
        });

        Terminal terminal = TerminalFactory.create(type);
        assertThat(terminal).isNotNull();
        assertThat(capturedConfig[0].rows()).isEqualTo(24);
        assertThat(capturedConfig[0].cols()).isEqualTo(80);
    }

    @Test
    void testCreateWithCustomConfig() {
        String type = uniqueType();
        var capturedConfig = new TerminalConfig[1];
        TerminalFactory.register(type, config -> {
            capturedConfig[0] = config;
            return createDummy(type);
        });

        TerminalConfig customConfig = TerminalConfig.builder().rows(43).cols(132).build();
        Terminal terminal = TerminalFactory.create(type, customConfig);

        assertThat(terminal).isNotNull();
        assertThat(capturedConfig[0]).isSameAs(customConfig);
        assertThat(capturedConfig[0].rows()).isEqualTo(43);
    }

    @Test
    void testCreateUnknownTypeThrows() {
        String unknown = "unknown-" + System.nanoTime();
        assertThatThrownBy(() -> TerminalFactory.create(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown terminal type");
    }

    @Test
    void testCreateUnknownTypeWithConfigThrows() {
        String unknown = "unknown-" + System.nanoTime();
        TerminalConfig config = TerminalConfig.builder().build();
        assertThatThrownBy(() -> TerminalFactory.create(unknown, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown terminal type");
    }

    @Test
    void testRegisteredTypes() {
        String type = uniqueType();
        TerminalFactory.register(type, config -> createDummy(type));
        String[] types = TerminalFactory.registeredTypes();

        boolean found = false;
        for (String t : types) {
            if (type.equals(t)) { found = true; break; }
        }
        assertThat(found).isTrue();
    }

    @Test
    void testRegisterOverwritesExisting() {
        String type = uniqueType();
        Terminal terminal1 = createDummy("v1");
        Terminal terminal2 = createDummy("v2");

        TerminalFactory.register(type, config -> terminal1);
        TerminalFactory.register(type, config -> terminal2);

        assertThat(TerminalFactory.lookup(type).create(TerminalConfig.builder().build()))
                .isSameAs(terminal2);
    }
}
