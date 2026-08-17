package ssg.legoflow.network.telnet.base;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TelnetOutputStreamTest {

    @Test
    void plainTextPassthrough() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TelnetOutputStream out = new TelnetOutputStream(baos)) {
            out.write("hello".getBytes());
        }
        assertThat(baos.toByteArray()).isEqualTo("hello".getBytes());
    }

    @Test
    void escapesIac() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TelnetOutputStream out = new TelnetOutputStream(baos)) {
            out.write(new byte[]{0x61, (byte) 0xFF, 0x62});
        }
        assertThat(baos.toByteArray())
                .isEqualTo(new byte[]{0x61, (byte) 0xFF, (byte) 0xFF, 0x62});
    }

    @Test
    void writeSingleByte() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TelnetOutputStream out = new TelnetOutputStream(baos)) {
            out.write('a');
        }
        assertThat(baos.toByteArray()).isEqualTo(new byte[]{'a'});
    }

    @Test
    void writeSingleIacByte() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TelnetOutputStream out = new TelnetOutputStream(baos)) {
            out.write(0xFF);
        }
        assertThat(baos.toByteArray())
                .isEqualTo(new byte[]{(byte) 0xFF, (byte) 0xFF});
    }

    @Test
    void sendCommand() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TelnetOutputStream out = new TelnetOutputStream(baos)) {
            out.sendCommand(TelnetCommand.AYT);
        }
        assertThat(baos.toByteArray())
                .isEqualTo(new byte[]{(byte) 0xFF, (byte) 0xF6});
    }

    @Test
    void sendNegotiate() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TelnetOutputStream out = new TelnetOutputStream(baos)) {
            out.sendNegotiate(TelnetCommand.DO, 1);
        }
        assertThat(baos.toByteArray())
                .isEqualTo(new byte[]{(byte) 0xFF, (byte) 0xFD, 1});
    }

    @Test
    void sendSubnegotiation() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TelnetOutputStream out = new TelnetOutputStream(baos)) {
            out.sendSubnegotiation(24, "xterm\0".getBytes());
        }
        byte[] expected = new byte[]{
                (byte) 0xFF, (byte) 0xFA, 24,
                'x', 't', 'e', 'r', 'm', 0,
                (byte) 0xFF, (byte) 0xF0
        };
        assertThat(baos.toByteArray()).isEqualTo(expected);
    }

    @Test
    void writeWithOffset() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TelnetOutputStream out = new TelnetOutputStream(baos)) {
            byte[] data = "abcdef".getBytes();
            out.write(data, 1, 3);
        }
        assertThat(baos.toByteArray()).isEqualTo("bcd".getBytes());
    }

    @Test
    void writeIntLoop() throws IOException {
        // Verify write(byte[]) uses write(int) loop correctly
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TelnetOutputStream out = new TelnetOutputStream(baos)) {
            out.write(new byte[]{0x01, (byte) 0xFF, 0x02});
        }
        assertThat(baos.toByteArray())
                .isEqualTo(new byte[]{0x01, (byte) 0xFF, (byte) 0xFF, 0x02});
    }
}
