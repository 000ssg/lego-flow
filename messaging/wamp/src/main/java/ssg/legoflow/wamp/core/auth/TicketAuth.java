package ssg.legoflow.wamp.core.auth;

import java.util.Map;

/**
 * WAMP Ticket Authentication.
 * Simple token-based authentication where the client presents a pre-shared ticket/token.
 *
 * <p>Flow:
 * <ol>
 *   <li>Client sends HELLO with {@code authid} and {@code authmethods: ["ticket"]}</li>
 *   <li>Router sends CHALLENGE with method "ticket"</li>
 *   <li>Client sends AUTHENTICATE with the ticket as the signature</li>
 *   <li>Router verifies the ticket and sends WELCOME or ABORT</li>
 * </ol>
 *
 * @since 0.1.0
 */
public class TicketAuth {

    /** The authentication method identifier for ticket auth. */
    public static final String AUTH_METHOD = "ticket";

    /**
     * Generates a challenge for ticket authentication.
     * The challenge is empty since the client simply presents its ticket.
     *
     * @return the challenge details map
     */
    public static Map<String, Object> generateChallenge() {
        return Map.of();
    }

    /**
     * Verifies a ticket against the expected value.
     *
     * @param expectedTicket the expected ticket value
     * @param actualTicket   the ticket provided by the client
     * @return {@code true} if the tickets match
     */
    public static boolean verify(String expectedTicket, String actualTicket) {
        return expectedTicket != null && expectedTicket.equals(actualTicket);
    }
}
