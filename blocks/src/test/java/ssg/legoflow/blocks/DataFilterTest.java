package ssg.legoflow.blocks;

import ssg.legoflow.blocks.demo.LoggingFilter;
import ssg.legoflow.blocks.demo.TransformFilter;
import ssg.legoflow.blocks.demo.ValidationFilter;
import ssg.legoflow.blocks.exceptions.StateTransitionException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class DataFilterTest {

    private final Context ctx = new DefaultContext();

    @Test
    void testValidationFilterAccepts() {
        var filter = new ValidationFilter<>(String.class, s -> s.length() > 0);

        String[] result = filter.filter(ctx, "hello", "world");

        assertThat(result).containsExactly("hello", "world");
    }

    @Test
    void testValidationFilterRejects() {
        var filter = new ValidationFilter<>(String.class, s -> s.length() > 3);

        String[] result = filter.filter(ctx, "hi", "hello", "yo", "world");

        assertThat(result).containsExactly("hello", "world");
    }

    @Test
    void testTransformFilter() {
        var filter = new TransformFilter<>(String.class, String::toUpperCase);

        String[] result = filter.filter(ctx, "hello", "world");

        assertThat(result).containsExactly("HELLO", "WORLD");
    }

    @Test
    void testLoggingFilterPassesThrough() {
        var filter = new LoggingFilter<>(String.class, "test");

        String[] result = filter.filter(ctx, "a", "b");

        assertThat(result).containsExactly("a", "b");
    }

    @Test
    void testFilterTracksStatistics() {
        var filter = new ValidationFilter<>(String.class, s -> s.startsWith("a"));

        filter.filter(ctx, "abc", "def", "aaa");

        assertThat(filter.getStatistics().getInCount(String.class)).isEqualTo(3);
        assertThat(filter.getStatistics().getOutCount(String.class)).isEqualTo(2);
    }

    @Test
    void testFilterInitialStateIsIdle() {
        var filter = new LoggingFilter<>(String.class, "test");

        assertThat(filter.getState()).isEqualTo(ProcessorState.IDLE);
    }

    @Test
    void testFilterCloseSetsStopped() {
        var filter = new LoggingFilter<>(String.class, "test");

        filter.close();

        assertThat(filter.getState()).isEqualTo(ProcessorState.STOPPED);
    }

    @Test
    void testFilterWithEmptyInput() {
        var filter = new ValidationFilter<>(String.class, s -> true);

        String[] result = filter.filter(ctx);

        assertThat(result).isEmpty();
    }

    @Test
    void testFilterCloseIsIdempotent() {
        var filter = new LoggingFilter<>(String.class, "test");

        filter.close();
        filter.close(); // should not throw

        assertThat(filter.getState()).isEqualTo(ProcessorState.STOPPED);
    }

    /** Helper subclass to expose {@code transitionTo} for testing. */
    private static class ExposedValidationFilter<T> extends ValidationFilter<T> {
        ExposedValidationFilter(Class<T> type, java.util.function.Predicate<T> predicate) {
            super(type, predicate);
        }
        void doTransition(ProcessorState s) { transitionTo(s); }
    }

    @Test
    void testFilterInvalidTransitionThrows() {
        var filter = new ExposedValidationFilter<>(String.class, s -> true);

        // IDLE cannot go to PAUSED
        assertThatThrownBy(() -> filter.doTransition(ProcessorState.PAUSED))
                .isInstanceOf(StateTransitionException.class);
    }

    @Test
    void testFilterWithNullInput() {
        var filter = new ValidationFilter<>(String.class, s -> true);

        // null data should pass through without NPE
        String[] result = filter.filter(ctx, (String[]) null);

        assertThat(result).isNull();
    }
}
