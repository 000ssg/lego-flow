package ssg.legoflow.xmpp.core;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * XMPP address (Jabber ID) as defined in RFC 6122.
 *
 * <p>A JID has the form {@code localpart@domainpart/resourcepart}, where localpart
 * and resourcepart are optional. The domainpart is always required.
 *
 * @param localpart    the optional local part (user identifier)
 * @param domainpart   the required domain part
 * @param resourcepart the optional resource part
 * @since 1.0.0
 */
public record JID(String localpart, String domainpart, String resourcepart) {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?)*$");
    private static final Pattern LOCALPART_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_.\\-]+$");
    private static final int MAX_LOCALPART_LENGTH = 1023;
    private static final int MAX_DOMAINPART_LENGTH = 1023;
    private static final int MAX_RESOURCEPART_LENGTH = 1023;

    /**
     * Characters prohibited in the localpart per RFC 7622 section 3.3.1.
     * This covers the most critical prohibited characters from the PRECIS
     * IdentifierClass applied to XMPP localparts.
     */
    private static final Set<Character> LOCALPART_PROHIBITED = Set.of(
            '"', '&', '\'', '/', ':', '<', '>', '@'
    );

    /**
     * Constructs a validated JID with PRECIS-inspired normalization (RFC 7622).
     *
     * <p>Normalization applied:
     * <ul>
     *   <li>Localpart: NFKC normalization via {@link Normalizer}, prohibited character
     *       check per RFC 7622 section 3.3.1</li>
     *   <li>Domainpart: lowercased per IDNA rules</li>
     *   <li>Resourcepart: NFC normalization</li>
     * </ul>
     *
     * @param localpart    the optional local part
     * @param domainpart   the required domain part
     * @param resourcepart the optional resource part
     * @throws IllegalArgumentException if the JID components are invalid per RFC 6122/7622
     */
    public JID {
        Objects.requireNonNull(domainpart, "domainpart must not be null");

        // --- Domainpart: lowercase (IDNA requirement) ---
        domainpart = domainpart.toLowerCase(java.util.Locale.ROOT);

        if (domainpart.isBlank()) {
            throw new IllegalArgumentException("domainpart must not be blank");
        }
        if (domainpart.length() > MAX_DOMAINPART_LENGTH) {
            throw new IllegalArgumentException("domainpart exceeds maximum length of " + MAX_DOMAINPART_LENGTH);
        }
        if (!DOMAIN_PATTERN.matcher(domainpart).matches()) {
            throw new IllegalArgumentException("Invalid domainpart: " + domainpart);
        }

        // --- Localpart: NFKC normalization + prohibited character check ---
        if (localpart != null) {
            localpart = Normalizer.normalize(localpart, Normalizer.Form.NFKC);
            if (localpart.isBlank()) {
                throw new IllegalArgumentException("localpart must not be blank if provided");
            }
            if (localpart.length() > MAX_LOCALPART_LENGTH) {
                throw new IllegalArgumentException("localpart exceeds maximum length of " + MAX_LOCALPART_LENGTH);
            }
            for (int i = 0; i < localpart.length(); i++) {
                char c = localpart.charAt(i);
                if (LOCALPART_PROHIBITED.contains(c)) {
                    throw new IllegalArgumentException(
                            "localpart contains prohibited character: '" + c + "'");
                }
            }
            if (!LOCALPART_PATTERN.matcher(localpart).matches()) {
                throw new IllegalArgumentException("Invalid localpart: " + localpart);
            }
        }

        // --- Resourcepart: NFC normalization ---
        if (resourcepart != null) {
            resourcepart = Normalizer.normalize(resourcepart, Normalizer.Form.NFC);
            if (resourcepart.isBlank()) {
                throw new IllegalArgumentException("resourcepart must not be blank if provided");
            }
            if (resourcepart.length() > MAX_RESOURCEPART_LENGTH) {
                throw new IllegalArgumentException("resourcepart exceeds maximum length of " + MAX_RESOURCEPART_LENGTH);
            }
        }
    }

    /**
     * Parses a JID from its string representation.
     *
     * @param jidString the JID string in the form {@code [localpart@]domainpart[/resourcepart]}
     * @return the parsed JID
     * @throws IllegalArgumentException if the string is not a valid JID
     */
    public static JID parse(String jidString) {
        Objects.requireNonNull(jidString, "JID string must not be null");
        if (jidString.isBlank()) {
            throw new IllegalArgumentException("JID string must not be blank");
        }

        String localpart = null;
        String domainpart;
        String resourcepart = null;

        String remaining = jidString;

        int atIndex = remaining.indexOf('@');
        if (atIndex >= 0) {
            localpart = remaining.substring(0, atIndex);
            remaining = remaining.substring(atIndex + 1);
        }

        int slashIndex = remaining.indexOf('/');
        if (slashIndex >= 0) {
            domainpart = remaining.substring(0, slashIndex);
            resourcepart = remaining.substring(slashIndex + 1);
        } else {
            domainpart = remaining;
        }

        return new JID(localpart, domainpart, resourcepart);
    }

    /**
     * Returns the bare JID (without resourcepart): {@code localpart@domainpart}.
     *
     * @return the bare JID string
     */
    public String toBareJid() {
        if (localpart != null) {
            return localpart + "@" + domainpart;
        }
        return domainpart;
    }

    /**
     * Returns the full JID: {@code localpart@domainpart/resourcepart}.
     *
     * @return the full JID string
     */
    public String toFullJid() {
        var sb = new StringBuilder();
        if (localpart != null) {
            sb.append(localpart).append('@');
        }
        sb.append(domainpart);
        if (resourcepart != null) {
            sb.append('/').append(resourcepart);
        }
        return sb.toString();
    }

    /**
     * Returns whether this JID has a localpart.
     *
     * @return true if localpart is present
     */
    public boolean hasLocalpart() {
        return localpart != null;
    }

    /**
     * Returns whether this JID has a resourcepart.
     *
     * @return true if resourcepart is present
     */
    public boolean hasResourcepart() {
        return resourcepart != null;
    }

    /**
     * Returns a new JID with the resource stripped.
     *
     * @return a bare JID
     */
    public JID toBare() {
        return new JID(localpart, domainpart, null);
    }

    /**
     * Returns a new JID with the specified resource.
     *
     * @param resource the resource part
     * @return a full JID with the given resource
     */
    public JID withResource(String resource) {
        return new JID(localpart, domainpart, resource);
    }

    @Override
    public String toString() {
        return toFullJid();
    }
}
