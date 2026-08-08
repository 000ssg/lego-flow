package ssg.legoflow.ftp.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;

import java.nio.ByteBuffer;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class FtpServerServiceTest {

    @Test void testBuilderBasic() {
        var svc = FtpServerService.builder().name("my-ftp").priority(50).build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-ftp");
    }

    @Test void testInitialState() {
        var svc = FtpServerService.builder().build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = FtpServerService.builder().build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = FtpServerService.builder().build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }
}
