package ssg.legoflow.network.modbus.data;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegisterDataTest {

    @Test
    void testCoil() {
        var coil = new Coil(100, true);
        assertThat(coil.address()).isEqualTo(100);
        assertThat(coil.value()).isTrue();
    }

    @Test
    void testRegister() {
        var reg = new Register(300, 100);
        assertThat(reg.address()).isEqualTo(300);
        assertThat(reg.value()).isEqualTo(100);
    }
}
