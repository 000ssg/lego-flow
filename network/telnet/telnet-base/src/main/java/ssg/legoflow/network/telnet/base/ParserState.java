package ssg.legoflow.network.telnet.base;

/**
 * States of the Telnet protocol parser state machine (RFC 854).
 *
 * <p>The parser transitions through these states as bytes arrive
 * on the wire. The DATA state is the default; all other states
 * are entered only after seeing an IAC byte.
 */
public enum ParserState {

    /**
     * Normal data transfer. Every byte is data except IAC (255).
     */
    DATA,

    /**
     * An IAC byte was just received; the next byte is a command.
     */
    COMMAND,

    /**
     * A negotiation command (WILL/WONT/DO/DONT) was received;
     * the next byte is the option code.
     */
    NEGOTIATE,

    /**
     * Inside a subnegotiation (SB...SE). All bytes are collected
     * until IAC SE is seen. IAC is escaped by doubling.
     */
    SUBNEGOTIATION;
}
