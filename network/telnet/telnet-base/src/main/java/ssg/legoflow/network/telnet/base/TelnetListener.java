package ssg.legoflow.network.telnet.base;

import java.util.List;

/**
 * Callback interface for Telnet protocol events.
 *
 * <p>Implementations receive notifications as the Telnet parser
 * processes bytes from the remote peer. Default implementations
 * are provided for all methods so subclasses only implement what
 * they need.
 *
 * @since 0.2.0
 */
public interface TelnetListener {

    /**
     * Called when application data is received (non-protocol bytes).
     *
     * <p>Data is delivered as soon as it is available; callers should
     * not assume that a single call contains a complete logical unit.
     *
     * @param data the application data bytes (IAC already stripped)
     */
    default void onData(List<Integer> data) {}

    /**
     * Called when a single-byte protocol command is received.
     *
     * <p>Covers: NOP, DM, BRK, IP, AO, AYT, EC, EL, GA.
     *
     * @param command the command
     */
    default void onCommand(TelnetCommand command) {}

    /**
     * Called when a negotiation command is received.
     *
     * <p>Covers: WILL, WONT, DO, DONT.
     *
     * @param command WILL, WONT, DO, or DONT
     * @param option  the option code (0–255)
     */
    default void onNegotiate(TelnetCommand command, int option) {}

    /**
     * Called when a subnegotiation (SB...SE) is received.
     *
     * <p>The option code identifies the type of subnegotiation
     * (e.g. TTYPE=24, NAWS=31), and the data contains the
     * subnegotiation payload (null-terminated for some options).
     *
     * @param option the option code
     * @param data   the subnegotiation payload bytes (may be empty)
     */
    default void onSubnegotiation(int option, List<Integer> data) {}

    /**
     * Called when a malformed protocol sequence is detected.
     *
     * <p>This may occur if an expected byte (e.g. the option byte
     * after WILL) is missing because the connection was closed.
     *
     * @param state the parser state when the error was detected
     */
    default void onProtocolError(ParserState state) {}
}
