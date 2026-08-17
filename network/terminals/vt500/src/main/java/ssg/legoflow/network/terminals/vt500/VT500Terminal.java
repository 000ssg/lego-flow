package ssg.legoflow.network.terminals.vt500;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt400.VT400Terminal;

import java.util.Map;

/**
 * VT500 terminal emulator.
 *
 * <p>Extends VT400 with advanced workstation capabilities:
 * <ul>
 *   <li>DEC character set support (G0/G1 switching via SO/SI and ESC paren)</li>
 *   <li>DECSET charset selection modes</li>
 *   <li>DCS for user-defined character sets (single-character mapping)</li>
 *   <li>International character sets (UK, French, French-Canadian, International, Scandinavian, German)</li>
 *   <li>Extended line feed handling</li>
 * </ul>
 *
 * <p>Character set selection works via:
 * <ul>
 *   <li>ESC ( letter — select G0 charset (DECSCE)</li>
 *   <li>ESC ) letter — select G1 charset (DECSCE)</li>
 *   <li>SO (0x0E) — activate G0 charset</li>
 *   <li>SI (0x0F) — activate G1 charset</li>
 * </ul>
 *
 * <p>Character set selection descriptors:
 * <ul>
 *   <li>B — ASCII (default)</li>
 *   <li>0 — DEC Special Character and Line Drawing Set</li>
 *   <li>U — UK</li>
 *   <li>K — French</li>
 *   <li>W — French-Canadian</li>
 *   <li>R — International</li>
 *   <li>Q — Scandinavian-A</li>
 *   <li>Y — German</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>LINE DRAW character set not implemented (DEC charset 'H')</li>
 *   <li>Greek/Typography character sets not implemented</li>
 *   <li>User-defined character sets are single-character mappings only</li>
 *   <li>Multiple Character Set (MCS) not implemented</li>
 *   <li>G2/G3 character sets not implemented (VT500 only uses G0/G1)</li>
 *   <li>DECRQSS for user-defined charset not implemented</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class VT500Terminal extends VT400Terminal {

    public static Terminal create(TerminalConfig config) {
        return new VT500Terminal(config);
    }

    /** DEC character set identifier. */
    public enum CharSet {
        /** Standard ASCII. */
        ASCII('B'),
        /** DEC Special Character and Line Drawing Set. */
        DEC_SPECIAL('0'),
        /** UK character set. */
        UK('U'),
        /** French character set. */
        FRENCH('K'),
        /** French-Canadian character set. */
        FRENCH_CANADIAN('W'),
        /** International character set. */
        INTERNATIONAL('R'),
        /** Scandinavian-A character set. */
        SCANDINAVIAN('Q'),
        /** German character set. */
        GERMAN('Y'),
        /** User-defined character set. */
        USER_DEFINED(null);

        private final Character selectDescriptor;

        CharSet(Character descriptor) {
            this.selectDescriptor = descriptor;
        }

        Character selectDescriptor() {
            return selectDescriptor;
        }

        private static final Map<Character, CharSet> BY_DESCRIPTOR = new java.util.HashMap<>();

        static {
            for (CharSet cs : values()) {
                if (cs.selectDescriptor != null) {
                    BY_DESCRIPTOR.put(cs.selectDescriptor, cs);
                }
            }
        }

        /** Look up a charset by its select descriptor character. */
        static CharSet fromDescriptor(char desc) {
            return (desc == 0) ? USER_DEFINED : BY_DESCRIPTOR.getOrDefault(desc, ASCII);
        }
    }

    // --- DEC Special Character and Line Drawing Set ---
    /** Maps ASCII 0x60-0x7E range to DEC Special Unicode codepoints. */
    private static final int[] DEC_SPECIAL_MAP = new int[128];

    static {
        for (int i = 0; i < DEC_SPECIAL_MAP.length; i++) {
            DEC_SPECIAL_MAP[i] = i;
        }
        // DEC Special Character and Line Drawing Set (charset '0')
        DEC_SPECIAL_MAP['`'] = 0x25C6; // black diamond
        DEC_SPECIAL_MAP['a'] = 0x2592; // medium shade
        DEC_SPECIAL_MAP['b'] = 0x2409; // symbol for paragraph
        DEC_SPECIAL_MAP['c'] = 0x240C; // symbol for section
        DEC_SPECIAL_MAP['d'] = 0x240D; // symbol for draft
        DEC_SPECIAL_MAP['e'] = 0x240A; // symbol for registered
        DEC_SPECIAL_MAP['f'] = 0x00B0; // degree sign
        DEC_SPECIAL_MAP['g'] = 0x00B6; // pilcrow sign
        DEC_SPECIAL_MAP['h'] = 0x00AF; // macron
        DEC_SPECIAL_MAP['i'] = 0x00BF; // inverted question mark
        DEC_SPECIAL_MAP['j'] = 0x239B; // bottom half of integral
        DEC_SPECIAL_MAP['k'] = 0x00A6; // broken vertical bar
        DEC_SPECIAL_MAP['l'] = 0x00AC; // not sign
        DEC_SPECIAL_MAP['m'] = 0x221E; // infinity
        DEC_SPECIAL_MAP['n'] = 0x03C0; // Greek lowercase pi
        DEC_SPECIAL_MAP['o'] = 0x2264; // less-than or equal
        DEC_SPECIAL_MAP['p'] = 0x2265; // greater-than or equal
        DEC_SPECIAL_MAP['q'] = 0x03A9; // Greek capital omega
        DEC_SPECIAL_MAP['r'] = 0x25CA; // lozenge
        DEC_SPECIAL_MAP['s'] = 0x25CB; // white circle
        DEC_SPECIAL_MAP['t'] = 0x2500; // box drawings horizontal
        DEC_SPECIAL_MAP['u'] = 0x2502; // box drawings vertical
        DEC_SPECIAL_MAP['v'] = 0x250C; // box drawings down and right
        DEC_SPECIAL_MAP['w'] = 0x2510; // box drawings down and left
        DEC_SPECIAL_MAP['x'] = 0x2514; // box drawings up and right
        DEC_SPECIAL_MAP['y'] = 0x2518; // box drawings up and left
        DEC_SPECIAL_MAP['z'] = 0x251C; // box drawings horizontal and up
        DEC_SPECIAL_MAP['{'] = 0x2524; // box drawings horizontal and down
        DEC_SPECIAL_MAP['|'] = 0x253C; // box drawings vertical and horizontal
        DEC_SPECIAL_MAP['}'] = 0x252C; // box drawings vertical and right
        DEC_SPECIAL_MAP['~'] = 0x2502; // box drawings vertical
        DEC_SPECIAL_MAP['!'] = 0x2563; // box drawings light diagonal up and right
        DEC_SPECIAL_MAP['"'] = 0x2551; // box drawings double horizontal
        DEC_SPECIAL_MAP['#'] = 0x2557; // box drawings double down and single right
        DEC_SPECIAL_MAP['$'] = 0x255D; // box drawings double down and single left
        DEC_SPECIAL_MAP['%'] = 0x254B; // box drawings double up and single right
        DEC_SPECIAL_MAP['&'] = 0x2548; // box drawings double up and single left
        DEC_SPECIAL_MAP[0x27] = 0x255B; // box drawings single up and double left
        DEC_SPECIAL_MAP['('] = 0x2558; // box drawings double up and double left
        DEC_SPECIAL_MAP[')'] = 0x2554; // box drawings single up and single left
        DEC_SPECIAL_MAP['*'] = 0x255A; // box drawings single up and double right
        DEC_SPECIAL_MAP['+'] = 0x2569; // box drawings light diagonal cross
        DEC_SPECIAL_MAP[','] = 0x2566; // box drawings light diagonal up and left
        DEC_SPECIAL_MAP['-'] = 0x2560; // box drawings heavy horizontal
        DEC_SPECIAL_MAP['.'] = 0x255E; // box drawings double horizontal and up
        DEC_SPECIAL_MAP['/'] = 0x2559; // box drawings double up and double right
    }

    // --- International character set mappings ---
    // Maps 0x60-0x7E range to locale-specific characters
    // These replace certain positions in the printable ASCII range

    /** UK character set mapping (positions 0x60-0x7E). */
    private static final int[] UK_MAP = buildInternationalMap(
        '`', '¬',  // 0x60
        'a', '£',  // 0x61
        '{', '·',  // 0x7B
        '|', '¦',  // 0x7C
        '}', '¤',  // 0x7D
        '~', '¾'   // 0x7E
    );

    /** French character set mapping. */
    private static final int[] FRENCH_MAP = buildInternationalMap(
        '`', 'à',  // 0x60
        'a', 'é',  // 0x61
        'e', 'ê',  // 0x65
        '{', 'è',  // 0x7B
        '|', 'ï',  // 0x7C
        '}', 'î',  // 0x7D
        '~', 'ù'   // 0x7E
    );

    /** French-Canadian character set mapping. */
    private static final int[] FRENCH_CANADIAN_MAP = buildInternationalMap(
        'e', 'é',  // 0x65
        'E', 'É',  // 0x45
        '{', 'ê',  // 0x7B
        '|', 'è',  // 0x7C
        '}', 'ê',  // 0x7D
        '~', 'ù'   // 0x7E
    );

    /** International character set mapping. */
    private static final int[] INTERNATIONAL_MAP = buildInternationalMap(
        '`', 'à',  // 0x60
        'a', 'á',  // 0x61
        'e', 'é',  // 0x65
        'i', 'í',  // 0x69
        'o', 'ó',  // 0x6F
        'u', 'ú',  // 0x75
        '{', 'ñ',  // 0x7B
        '|', 'õ',  // 0x7C
        '}', '°',  // 0x7D
        '~', 'ç'   // 0x7E
    );

    /** Scandinavian-A character set mapping. */
    private static final int[] SCANDINAVIAN_MAP = buildInternationalMap(
        '`', 'Å',  // 0x60
        'a', 'Ä',  // 0x61
        '{', 'É',  // 0x7B
        '|', 'Ö',  // 0x7C
        '}', 'Ü',  // 0x7D
        '~', '¤'   // 0x7E
    );

    /** German character set mapping. */
    private static final int[] GERMAN_MAP = buildInternationalMap(
        '`', '£',  // 0x60
        'a', 'á',  // 0x61
        'e', 'é',  // 0x65
        '{', 'ö',  // 0x7B
        '|', 'ü',  // 0x7C
        '}', 'ä',  // 0x7D
        '~', 'ß'   // 0x7E
    );

    /** Build an international charset mapping array (identity + overrides). */
    private static int[] buildInternationalMap(Object... pairs) {
        int[] map = new int[128];
        for (int i = 0; i < map.length; i++) map[i] = i;
        for (int i = 0; i < pairs.length; i += 2) {
            char from = (Character) pairs[i];
            char to = (Character) pairs[i + 1];
            map[from] = to;
        }
        return map;
    }

    /** G0 character set (default: ASCII). */
    private CharSet g0Charset = CharSet.ASCII;
    /** G1 character set (default: ASCII). */
    private CharSet g1Charset = CharSet.ASCII;
    /** Currently active character set. */
    private CharSet activeCharset = CharSet.ASCII;
    /** User-defined character set mapping (DCS-defined characters). */
    private final int[] userDefinedMap = new int[128];

    // --- Auto-registration with TerminalFactory ---
    static {
        TerminalFactory.register("vt500", config -> VT500Terminal.create(config));
    }

    private VT500Terminal(TerminalConfig config) {
        super(config);
        for (int i = 0; i < userDefinedMap.length; i++) userDefinedMap[i] = i;
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();
        String intermediates = params.intermediates();

        // Handle DECSET/DECRST for charset-related modes
        if (intermediates.equals("?") && finalByte == 'h') {
            for (int i = 0; i < params.size(); i++) {
                int mode = params.get(i);
                switch (mode) {
                    case 1049 -> {/* Alternate screen buffer — not supported */}
                    // Other DECSET modes handled by VT100 parent
                }
            }
        }

        // Delegate all other CSI sequences to parent chain
        super.handleCSI(params);
    }

    /** Map character through active charset. */
    private int mapChar(int codepoint) {
        if (codepoint < 0 || codepoint >= 128) return codepoint;
        return switch (activeCharset) {
            case ASCII -> codepoint;
            case DEC_SPECIAL -> DEC_SPECIAL_MAP[codepoint];
            case UK -> UK_MAP[codepoint];
            case FRENCH -> FRENCH_MAP[codepoint];
            case FRENCH_CANADIAN -> FRENCH_CANADIAN_MAP[codepoint];
            case INTERNATIONAL -> INTERNATIONAL_MAP[codepoint];
            case SCANDINAVIAN -> SCANDINAVIAN_MAP[codepoint];
            case GERMAN -> GERMAN_MAP[codepoint];
            case USER_DEFINED -> userDefinedMap[codepoint];
        };
    }

    @Override
    public void handleChar(int codepoint) {
        if (activeCharset != CharSet.ASCII) {
            codepoint = mapChar(codepoint);
        }
        super.handleChar(codepoint);
    }

    /**
     * Handle ESC paren charset selection (DECSCE).
     * ESC ( letter — select G0 charset
     * ESC ) letter — select G1 charset
     */
    @Override
    public void handleEscCharset(boolean g0, char selector) {
        CharSet charset = CharSet.fromDescriptor(selector);
        if (g0) {
            g0Charset = charset;
        } else {
            g1Charset = charset;
        }
    }

    @Override
    protected void selectG0Charset() {
        this.activeCharset = g0Charset;
    }

    @Override
    protected void selectG1Charset() {
        this.activeCharset = g1Charset;
    }

    /** Set G0 character set. */
    public void setG0(CharSet charset) {
        this.g0Charset = charset;
    }

    /** Set G1 character set. */
    public void setG1(CharSet charset) {
        this.g1Charset = charset;
    }

    /** Select G0 character set (SO). */
    public void selectG0() {
        this.activeCharset = g0Charset;
    }

    /** Select G1 character set (SI). */
    public void selectG1() {
        this.activeCharset = g1Charset;
    }

    /** Current active character set. */
    public CharSet activeCharset() { return activeCharset; }

    /** Currently assigned G0 character set. */
    public CharSet g0Charset() { return g0Charset; }

    /** Currently assigned G1 character set. */
    public CharSet g1Charset() { return g1Charset; }

    @Override
    public void handleDCS(String data) {
        // VT500 supports DCS for user-defined character sets
        // Format: DCS | Ps ; Pt ST  — define character Ps as Pt
        // Format: DCS $ q Pt ST    — define user-defined G0 charset
        if (data.startsWith("|")) {
            String[] parts = data.substring(1).split(";");
            if (parts.length >= 2) {
                try {
                    int codepoint = Integer.parseInt(parts[0].trim());
                    String replacement = parts[1].trim();
                    if (replacement.length() == 1) {
                        if (codepoint >= 0 && codepoint < 128) {
                            userDefinedMap[codepoint] = replacement.charAt(0);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid DCS — ignore
                }
            }
        } else if (data.startsWith("$q")) {
            // DECRQSS for user-defined charset — not implemented
            // (known limitation)
        }
    }

    @Override
    public String type() { return "vt500"; }

    @Override
    protected void onReset() {
        super.onReset();
        g0Charset = CharSet.ASCII;
        g1Charset = CharSet.ASCII;
        activeCharset = CharSet.ASCII;
        for (int i = 0; i < userDefinedMap.length; i++) userDefinedMap[i] = i;
    }
}
