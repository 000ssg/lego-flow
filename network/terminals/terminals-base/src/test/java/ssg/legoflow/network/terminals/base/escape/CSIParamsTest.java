package ssg.legoflow.network.terminals.base.escape;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CSIParamsTest {

    @Test
    void testEmptyParams() {
        CSIParams p = new CSIParams(List.of(), "", 'J');
        assertThat(p.size()).isZero();
        assertThat(p.isEmpty()).isTrue();
        assertThat(p.get(0, 42)).isEqualTo(42);
    }

    @Test
    void testSingleParam() {
        CSIParams p = new CSIParams(List.of(10), "", 'H');
        assertThat(p.get(0)).isEqualTo(10);
        assertThat(p.get(1, 1)).isEqualTo(1);
    }

    @Test
    void testMultipleParams() {
        CSIParams p = new CSIParams(List.of(2, 10, 0), "", 'H');
        assertThat(p.get(0)).isEqualTo(2);
        assertThat(p.get(1)).isEqualTo(10);
        assertThat(p.get(2)).isZero();
    }

    @Test
    void testOutOfBoundsReturnsDefault() {
        CSIParams p = new CSIParams(List.of(5), "", 'm');
        assertThat(p.get(0)).isEqualTo(5);
        assertThat(p.get(1, 99)).isEqualTo(99);
    }

    @Test
    void testNullParamsTreatedAsEmpty() {
        CSIParams p = new CSIParams(null, "", 'm');
        assertThat(p.size()).isZero();
    }

    @Test
    void testNullIntermediatesTreatedAsEmpty() {
        CSIParams p = new CSIParams(List.of(1), null, 'm');
        assertThat(p.intermediates()).isEmpty();
    }

    @Test
    void testToStringContainsValues() {
        CSIParams p = new CSIParams(List.of(38, 5, 196), "", 'm');
        assertThat(p.toString()).contains("38");
        assertThat(p.toString()).contains("5");
        assertThat(p.toString()).contains("196");
    }
}
