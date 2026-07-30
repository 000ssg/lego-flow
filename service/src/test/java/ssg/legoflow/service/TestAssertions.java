package ssg.legoflow.service;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retry-based assertion utilities for timing-sensitive tests.
 * Replaces blind {@code Thread.sleep()} with proper synchronization primitives
 * and exponential-backoff retries to eliminate flaky failures under parallel execution (-T 1C).
 */
public final class TestAssertions {

    private TestAssertions() {}

    /**
     * Waits until the supplied condition evaluates to {@code true}, applying exponential backoff.
     *
     * @param condition    predicate that returns {@code true} when the expected state is reached
     * @param timeout      maximum time to wait before failing
     * @param initialDelay initial delay between checks (ms); doubles each iteration
     * @return {@code true} if condition was met within the timeout
     */
    public static boolean waitForCondition(java.util.function.BooleanSupplier condition,
                                   Duration timeout, long initialDelay) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        long delay = initialDelay;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            delay = Math.min(delay * 2, 1000); // cap at 1s
        }
        return false;
    }

    /**
     * Waits for a {@link Callable} to succeed (return non-null or not throw) within timeout.
     * Uses exponential backoff starting from {@code initialDelay}.
     *
     * @return the callable result, or null if timeout exceeded
     */
    public static <T> T waitFor(Callable<T> callable, Duration timeout, long initialDelay) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        long delay = initialDelay;
        while (System.currentTimeMillis() < deadline) {
            try {
                T result = callable.call();
                if (result != null) {
                    return result;
                }
            } catch (Exception ignored) {
                // retry on any exception
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            delay = Math.min(delay * 2, 1000);
        }
        return null;
    }

    /**
     * Asserts a condition is true within the given timeout, with exponential backoff.
     * Fails with a descriptive message if timeout is exceeded.
     */
    public static void assertThatCondition(String description, java.util.function.BooleanSupplier condition,
                                   Duration timeout) {
        boolean met = waitForCondition(condition, timeout, 20);
        assertThat(met).as("%s was not satisfied within %s", description, timeout).isTrue();
    }

    /**
     * Asserts a supplier produces an expected value within the given timeout.
     */
    public static <T> void assertThatEventually(String description, Supplier<T> actualSupplier,
                                         T expected, Duration timeout) {
        boolean met = waitForCondition(() -> expected.equals(actualSupplier.get()),
                                       timeout, 20);
        if (!met) {
            T actual = actualSupplier.get();
            assertThat(expected).as("%s: expected within %s", description, timeout)
                    .isEqualTo(actual);
        }
    }

    /**
     * Waits for an {@link AtomicBoolean} flag to become true.
     */
    public static void waitForFlag(AtomicBoolean flag, Duration timeout) {
        assertThatCondition("flag assertion", flag::get, timeout);
    }
}
