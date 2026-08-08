package ssg.legoflow.network.dns.resolver;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.ResponseCode;

import static org.assertj.core.api.Assertions.*;

class DnsResolverExtendedTest {

    @Test void responseCodeValues() {
        for (var r : ResponseCode.values()) {
            assertThat(r.name()).isNotBlank();
            assertThat(r.value()).isNotNull();
        }
    }
}
