package ssg.legoflow.network.terminals.base.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TerminalConfigTest {

    @Test
    void testDefaultConfig() {
        TerminalConfig config = TerminalConfig.builder().build();
        assertThat(config.rows()).isEqualTo(24);
        assertThat(config.cols()).isEqualTo(80);
        assertThat(config.scrollHistory()).isEqualTo(512);
        assertThat(config.colorDepth()).isZero();
        assertThat(config.title()).isEmpty();
        assertThat(config.autoWrap()).isTrue();
        assertThat(config.originMode()).isFalse();
    }

    @Test
    void testCustomConfig() {
        TerminalConfig config = TerminalConfig.builder()
                .rows(43)
                .cols(132)
                .scrollHistory(1024)
                .colorDepth(256)
                .title("My Terminal")
                .iconTitle("term")
                .autoWrap(false)
                .originMode(true)
                .build();
        assertThat(config.rows()).isEqualTo(43);
        assertThat(config.cols()).isEqualTo(132);
        assertThat(config.scrollHistory()).isEqualTo(1024);
        assertThat(config.colorDepth()).isEqualTo(256);
        assertThat(config.title()).isEqualTo("My Terminal");
        assertThat(config.iconTitle()).isEqualTo("term");
        assertThat(config.autoWrap()).isFalse();
        assertThat(config.originMode()).isTrue();
    }

    @Test
    void testInvalidRows() {
        assertThatThrownBy(() -> TerminalConfig.builder().rows(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidCols() {
        assertThatThrownBy(() -> TerminalConfig.builder().cols(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidScrollHistory() {
        assertThatThrownBy(() -> TerminalConfig.builder().scrollHistory(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullTitleDefaultsToEmpty() {
        TerminalConfig config = TerminalConfig.builder().title(null).build();
        assertThat(config.title()).isEmpty();
    }

    @Test
    void testEquality() {
        TerminalConfig a = TerminalConfig.builder().rows(24).cols(80).build();
        TerminalConfig b = TerminalConfig.builder().rows(24).cols(80).build();
        TerminalConfig c = TerminalConfig.builder().rows(43).cols(132).build();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testToStringContainsKeyFields() {
        TerminalConfig config = TerminalConfig.builder().rows(43).cols(132).title("test").build();
        String s = config.toString();
        assertThat(s).contains("rows=43");
        assertThat(s).contains("cols=132");
        assertThat(s).contains("title=test");
    }
}
