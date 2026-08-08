package ssg.legoflow.auth.gssapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link GssException} including code paths that were previously uncovered.
 */
class GssExceptionTest {

    @Test
    void testMessageOnlyConstructor() {
        GssException ex = new GssException("test message");
        assertThat(ex.getMessage()).isEqualTo("test message");
        assertThat(ex.getMajorCode()).isZero();
        assertThat(ex.getMinorCode()).isZero();
        assertThat(ex.getGssException()).isNull();
    }

    @Test
    void testWithGenericThrowableCause() {
        RuntimeException cause = new RuntimeException("underlying error");
        GssException ex = new GssException("wrapper", cause);
        assertThat(ex.getMessage()).contains("wrapper");
        assertThat(ex.getMessage()).contains("underlying error");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMajorCode()).isZero();
        assertThat(ex.getMinorCode()).isZero();
        assertThat(ex.getGssException()).isNull();
    }

    @Test
    void testWithGenericCause() throws Exception {
        GssException wrapper = new GssException("context failed", 
                new java.io.IOException("simulated failure"));
        
        assertThat(wrapper.getMessage()).contains("context failed");
        assertThat(wrapper.getMajorCode()).isZero();
        assertThat(wrapper.getMinorCode()).isZero();
        assertThat(wrapper.getGssException()).isNull(); // cause is IOException, not GSSException
    }

    @Test
    void testMessageContainsCauseMessage() {
        Exception cause = new Exception("cause details");
        GssException ex = new GssException("my wrapper", cause);
        
        assertThat(ex.getMessage()).isEqualTo("my wrapper: cause details");
    }

    @Test
    void testSuppressedExceptions() throws Exception {
        GssException ex1 = new GssException("first");
        GssException ex2 = new GssException("second");
        ex1.addSuppressed(ex2);
        
        Throwable[] suppressed = ex1.getSuppressed();
        assertThat(suppressed).hasSize(1);
        assertThat(suppressed[0]).isSameAs(ex2);
    }

    @Test
    void testCauseChain() throws Exception {
        RuntimeException rootCause = new RuntimeException("root");
        GssException wrapped = new GssException("middle", rootCause);
        GssException outer = new GssException("outer", wrapped);
        
        assertThat(outer.getCause()).isSameAs(wrapped);
        assertThat(((GssException) outer.getCause()).getCause()).isSameAs(rootCause);
    }

    @Test
    void testRethrowPreservesStackTrace() {
        GssException ex = null;
        try {
            methodThatThrows();
        } catch (GssException e) {
            ex = e;
        }
        
        assertThat(ex).isNotNull();
        assertThat(ex.getStackTrace()).isNotEmpty();
    }

    private void methodThatThrows() throws GssException {
        throw new GssException("test exception from method");
    }

    @Test
    void testNullMessageWithCause() {
        RuntimeException cause = new RuntimeException("cause");
        GssException ex = new GssException(null, cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
