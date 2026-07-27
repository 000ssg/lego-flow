package ssg.legoflow.rpc.grpc.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MethodTypeTest {

    @Test
    void testAllFourTypes() {
        assertThat(MethodType.values()).hasSize(4);
    }

    @Test
    void testUnary() {
        assertThat(MethodType.UNARY).isNotNull();
    }

    @Test
    void testServerStreaming() {
        assertThat(MethodType.SERVER_STREAMING).isNotNull();
    }

    @Test
    void testClientStreaming() {
        assertThat(MethodType.CLIENT_STREAMING).isNotNull();
    }

    @Test
    void testBidiStreaming() {
        assertThat(MethodType.BIDI_STREAMING).isNotNull();
    }
}
