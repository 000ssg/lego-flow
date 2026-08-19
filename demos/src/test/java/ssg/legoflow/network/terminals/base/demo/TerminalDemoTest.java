package ssg.legoflow.network.terminals.base.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import ssg.legoflow.network.terminals.base.io.KeyTranslator;
import ssg.legoflow.network.terminals.base.io.Terminal;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Functional tests for terminal demo scenarios.
 *
 * <p>These tests use a mock terminal since concrete terminal types
 * (vt100, xterm, etc.) are in separate modules.
 */
class TerminalDemoTest {

    private static final char LF = 10;
    private static final char FF = 12;
    private static final char VT = 11;
    private static final char CR = 13;

    @Test
    void testTerminalCreationAndRender() {
        TerminalConfig config = TerminalConfig.builder()
                .rows(10)
                .cols(40)
                .build();

        Terminal terminal = createMockTerminal(config);
        terminal.feed("Hello" + LF + "World");
        List<String> lines = terminal.render();

        assertThat(lines).hasSize(10);
        assertThat(lines.get(0)).contains("Hello");
        assertThat(lines.get(1)).contains("World");
    }

    @Test
    void testCursorMovement() {
        Terminal terminal = createMockTerminal(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("Hello" + LF + "World");

        assertThat(terminal.cursor().row()).isEqualTo(2);
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    @Test
    void testReset() {
        Terminal terminal = createMockTerminal(TerminalConfig.builder()
                .rows(10).cols(40).build());
        terminal.feed("Some text");

        terminal.reset();

        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testKeyTranslation() {
        var translator = new KeyTranslator();

        assertThat(translator.translate("f1")).isNotNull();
        assertThat(translator.translate("up")).isNotNull();
        assertThat(translator.translate("tab")).isEqualTo(new byte[]{9});
    }

    @Test
    void testTerminalConfigDefaults() {
        TerminalConfig config = TerminalConfig.builder().build();

        assertThat(config.rows()).isEqualTo(24);
        assertThat(config.cols()).isEqualTo(80);
    }

    static Terminal createMockTerminal(TerminalConfig config) {
        DisplayModel display = new DisplayModel(config);
        return new Terminal() {
            @Override public void feed(byte[] data) {
                for (byte b : data) {
                    int ch = b & 0xFF;
                    if (ch >= 0x20 && ch <= 0x7E) {
                        display.putChar(ch);
                    } else if (ch == LF || ch == FF || ch == VT) {
                        int r = display.cursor().row() + 1;
                        display.cursor().setPos(r, 1);
                    } else if (ch == CR) {
                        display.cursor().setPos(display.cursor().row(), 1);
                    }
                }
            }

            @Override public void feed(String text) {
                for (int i = 0; i < text.length(); i++) {
                    feed(new byte[]{(byte) text.charAt(i)});
                }
            }

            @Override public List<String> render() {
                return display.screen().renderAll();
            }

            @Override public Cursor cursor() {
                return display.cursor();
            }

            @Override public TermAttr currentAttr() {
                return display.currentAttr();
            }

            @Override public TerminalConfig config() {
                return config;
            }

            @Override public DisplayModel displayModel() {
                return display;
            }

            @Override public void addEventListener(TerminalEventListener listener) {}

            @Override public void removeEventListener(TerminalEventListener listener) {}

            @Override public void reset() {
                display.cursor().setPos(1, 1);
            }

            @Override public String type() {
                return "mock";
            }

            @Override public String title() {
                return "Mock Terminal";
            }

            @Override public boolean supportsColor() {
                return false;
            }
        };
    }
}
