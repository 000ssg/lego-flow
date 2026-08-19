package ssg.legoflow.wamp.demo.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutionException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
class CalculatorDemoTest {

    private CalculatorServiceDemo calc;

    @BeforeEach
    void setUp() {
        calc = new CalculatorServiceDemo();
        calc.setup();
    }

    @Test
    void testAdd() {
        assertThat(calc.add(3, 5)).isCloseTo(8.0, within(0.001));
    }

    @Test
    void testAddNegativeNumbers() {
        assertThat(calc.add(-10, 4)).isCloseTo(-6.0, within(0.001));
    }

    @Test
    void testMultiply() {
        assertThat(calc.multiply(6, 7)).isCloseTo(42.0, within(0.001));
    }

    @Test
    void testMultiplyByZero() {
        assertThat(calc.multiply(100, 0)).isCloseTo(0.0, within(0.001));
    }

    @Test
    void testDivide() {
        assertThat(calc.divide(10, 4)).isCloseTo(2.5, within(0.001));
    }

    @Test
    void testDivideByZeroReturnsError() {
        var future = calc.divideAsync(10, 0);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void testSequentialOperations() {
        double sum = calc.add(10, 20);
        double product = calc.multiply(sum, 3);

        assertThat(product).isCloseTo(90.0, within(0.001));
    }
}
