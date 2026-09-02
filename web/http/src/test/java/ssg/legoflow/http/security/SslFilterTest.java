package ssg.legoflow.http.security;

import ssg.legoflow.blocks.DefaultContext;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class SslFilterTest {

    @Test
    void testEncryptModeCreation() {
        var config = new SslConfig();
        var filter = new SslFilter(config, SslFilter.Mode.ENCRYPT);

        assertThat(filter.getMode()).isEqualTo(SslFilter.Mode.ENCRYPT);
        assertThat(filter.getConfig()).isSameAs(config);
    }

    @Test
    void testDecryptModeCreation() {
        var config = new SslConfig();
        var filter = new SslFilter(config, SslFilter.Mode.DECRYPT);

        assertThat(filter.getMode()).isEqualTo(SslFilter.Mode.DECRYPT);
    }

    @Test
    void testFilterPassesDataThrough() {
        var config = new SslConfig();
        var filter = new SslFilter(config, SslFilter.Mode.ENCRYPT);
        var ctx = new DefaultContext();
        var data = ByteBuffer.wrap("Hello TLS".getBytes(StandardCharsets.UTF_8));

        ByteBuffer[] result = filter.filter(ctx, data);

        assertThat(result).hasSize(1);
        var bytes = new byte[result[0].remaining()];
        result[0].get(bytes);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("Hello TLS");
    }

    @Test
    void testFilterMultipleBuffers() {
        var config = new SslConfig();
        var filter = new SslFilter(config, SslFilter.Mode.ENCRYPT);
        var ctx = new DefaultContext();
        var buf1 = ByteBuffer.wrap("chunk1".getBytes(StandardCharsets.UTF_8));
        var buf2 = ByteBuffer.wrap("chunk2".getBytes(StandardCharsets.UTF_8));

        ByteBuffer[] result = filter.filter(ctx, buf1, buf2);

        assertThat(result).hasSize(2);
    }

    @Test
    void testFilterWithSslConfigProtocols() {
        var config = new SslConfig();
        config.setProtocols(java.util.List.of("TLSv1.3"));
        var filter = new SslFilter(config, SslFilter.Mode.ENCRYPT);

        assertThat(filter.getConfig().getProtocols()).containsExactly("TLSv1.3");
    }

    @Test
    void testFilterEmptyData() {
        var config = new SslConfig();
        var filter = new SslFilter(config, SslFilter.Mode.DECRYPT);
        var ctx = new DefaultContext();

        ByteBuffer[] result = filter.filter(ctx);

        assertThat(result).isEmpty();
    }
}
