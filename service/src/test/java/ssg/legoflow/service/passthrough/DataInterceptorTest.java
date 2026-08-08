package ssg.legoflow.service.passthrough;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

class DataInterceptorTest {

    @Test
    void testDefaultOnLocalToRemotePassesThrough() {
        DataInterceptor interceptor = new DataInterceptor() {};
        
        ByteBuffer data = ByteBuffer.wrap(new byte[]{1, 2, 3, 4});
        assertThat(interceptor.onLocalToRemote(null, data)).isSameAs(data);
    }

    @Test
    void testDefaultOnRemoteToLocalPassesThrough() {
        DataInterceptor interceptor = new DataInterceptor() {};
        
        ByteBuffer data = ByteBuffer.wrap(new byte[]{5, 6, 7, 8});
        assertThat(interceptor.onRemoteToLocal(null, data)).isSameAs(data);
    }

    @Test
    void testCustomInterceptorCanTransformData() {
        DataInterceptor interceptor = new DataInterceptor() {
            @Override
            public ByteBuffer onLocalToRemote(EstablishedConnection conn, ByteBuffer data) {
                if (data == null) return null;
                byte[] copy = new byte[data.remaining()];
                data.get(copy);
                return ByteBuffer.wrap(copy);
            }
        };
        
        ByteBuffer input = ByteBuffer.wrap(new byte[]{1, 2, 3});
        assertThat(interceptor.onLocalToRemote(null, input)).isNotNull();
    }

    @Test
    void testCustomInterceptorCanReturnNull() {
        DataInterceptor interceptor = new DataInterceptor() {
            @Override
            public ByteBuffer onLocalToRemote(EstablishedConnection conn, ByteBuffer data) {
                return null;
            }
        };
        
        assertThat(interceptor.onLocalToRemote(null, ByteBuffer.wrap(new byte[]{1, 2}))).isNull();
    }

    @Test
    void testEmptyBufferPassthrough() {
        DataInterceptor interceptor = new DataInterceptor() {};
        ByteBuffer empty = ByteBuffer.allocate(0);
        
        assertThat(interceptor.onLocalToRemote(null, empty)).isSameAs(empty);
        assertThat(interceptor.onRemoteToLocal(null, empty)).isSameAs(empty);
    }

    @Test
    void testNullBufferPassthrough() {
        DataInterceptor interceptor = new DataInterceptor() {};
        
        assertThat(interceptor.onLocalToRemote(null, null)).isNull();
        assertThat(interceptor.onRemoteToLocal(null, null)).isNull();
    }

    @Test
    void testCustomBidirectionalInterception() {
        DataInterceptor interceptor = new DataInterceptor() {
            @Override
            public ByteBuffer onLocalToRemote(EstablishedConnection conn, ByteBuffer data) {
                if (data != null && data.remaining() > 0) {
                    data.put(0, (byte)99);
                    data.rewind();
                }
                return data;
            }

            @Override
            public ByteBuffer onRemoteToLocal(EstablishedConnection conn, ByteBuffer data) {
                if (data != null && data.remaining() > 0) {
                    data.put(0, (byte)88);
                    data.rewind();
                }
                return data;
            }
        };
        
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{1, 2, 3});
        assertThat(interceptor.onLocalToRemote(null, buf).get(0)).isEqualTo((byte)99);
        
        ByteBuffer buf2 = ByteBuffer.wrap(new byte[]{4, 5, 6});
        assertThat(interceptor.onRemoteToLocal(null, buf2).get(0)).isEqualTo((byte)88);
    }
}
