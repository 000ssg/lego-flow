package ssg.legoflow.network.telnet.base.demo;

import ssg.legoflow.network.telnet.base.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates Telnet protocol parsing and connection handling.
 *
 * <p>Shows IAC escaping, command parsing, and data flow
 * through a TelnetConnection.
 *
 * @since 0.2.0
 */
public final class TelnetDemo {

    private TelnetDemo() {}

    public static void demonstrate() {
        List<byte[]> received = new ArrayList<>();

        TelnetConnection connection = TelnetConnection.builder()
                .writer(data -> System.out.println("Sent " + data.length + " bytes"))
                .onData(received::add)
                .build();

        connection.feed("Hello".getBytes());
        connection.flush();

        connection.send("World");

        System.out.println("Received " + received.size() + " data blocks");
    }
}
