package ssg.legoflow.network.ldap.codec;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.ldap.protocol.*;
import ssg.legoflow.network.ldap.filter.SearchFilter;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
class LdapCodecExtendedTest {

    @Test void encodeDecodeBindRequest() {
        var bind = BindRequest.simple("cn=admin", "password");
        var msg = LdapMessage.of(1, bind);
        assertThat(LdapCodec.encode(msg)).isNotNull();
    }

    @Test void encodeToBytes() {
        var unbind = new UnbindRequest();
        var msg = LdapMessage.of(1, unbind);
        assertThat(LdapCodec.encodeToBytes(msg)).isNotEmpty();
    }

    @Test void codecHasBufferedDataEmpty() {
        assertThat(new LdapCodec().hasBufferedData()).isFalse();
    }

    @Test void decodeStreamEmptyReturnsEmptyList() {
        var codec = new LdapCodec();
        assertThat(codec.decodeStream(ByteBuffer.wrap(new byte[0]))).isEmpty();
    }

    @Test void tryDecodeInsufficientData() {
        ByteBuffer partial = ByteBuffer.wrap(new byte[]{0x30});
        assertThat(LdapCodec.tryDecode(partial)).isNull();
    }

    @Test void encodeSearchRequest() {
        var search = SearchRequest.subtree("dc=example", SearchFilter.present("cn"));
        var msg = LdapMessage.of(1, search);
        assertThat(LdapCodec.encode(msg)).isNotNull();
    }

    @Test void codecExceptionConstructor() {
        assertThat(new LdapCodecException("err").getMessage()).isEqualTo("err");
    }

    @Test void codecExceptionWithCause() {
        var cause = new IllegalArgumentException("root");
        assertThat(new LdapCodecException("w", cause).getCause()).isSameAs(cause);
    }

    @Test void encodeSearchResultDone() {
        var msg = LdapMessage.of(3, SearchResultDone.success());
        assertThat(LdapCodec.encode(msg)).isNotNull();
    }

    @Test void encodeDecodeRoundTrip() {
        var bind = BindRequest.simple("cn=admin", "pass");
        byte[] bytes = LdapCodec.encodeToBytes(LdapMessage.of(1, bind));
        assertThat(LdapCodec.decode(bytes)).isNotNull();
    }
}
