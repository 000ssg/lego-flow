package ssg.legoflow.network.terminals.base.io;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates keyboard input into terminal-compatible escape sequences.
 *
 * <p>When a terminal emulator receives keyboard input from an application
 * (e.g., a Swing textarea or a web textarea), this class converts it to the
 * byte sequences that applications running in the terminal expect.
 *
 * <p>Handles:
 * <ul>
 *   <li>Normal printable characters</li>
 *   <li>Arrow keys (CSI A/B/C/D)</li>
 *   <li>Function keys (CSI P, 11~–24~)</li>
 *   <li>Edit keys (Home, End, Insert, Delete, Page Up/Down)</li>
 *   <li>Control characters (Ctrl+A = \x01, etc.)</li>
 *   <li>Tab, Enter, Backspace, Escape</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class KeyTranslator {

    private static final Map<String, String> KEY_MAP = new HashMap<>();

    static {
        // Arrow keys
        KEY_MAP.put("up", "\u001B[A");
        KEY_MAP.put("down", "\u001B[B");
        KEY_MAP.put("right", "\u001B[C");
        KEY_MAP.put("left", "\u001B[D");

        // Navigation
        KEY_MAP.put("home", "\u001B[H");
        KEY_MAP.put("end", "\u001B[F");
        KEY_MAP.put("insert", "\u001B[2~");
        KEY_MAP.put("delete", "\u001B[3~");
        KEY_MAP.put("pageup", "\u001B[5~");
        KEY_MAP.put("pagedown", "\u001B[6~");

        // Function keys (F1–F12)
        KEY_MAP.put("f1", "\u001BOP");
        KEY_MAP.put("f2", "\u001BOQ");
        KEY_MAP.put("f3", "\u001BOR");
        KEY_MAP.put("f4", "\u001BOS");
        KEY_MAP.put("f5", "\u001B[15~");
        KEY_MAP.put("f6", "\u001B[17~");
        KEY_MAP.put("f7", "\u001B[18~");
        KEY_MAP.put("f8", "\u001B[19~");
        KEY_MAP.put("f9", "\u001B[20~");
        KEY_MAP.put("f10", "\u001B[21~");
        KEY_MAP.put("f11", "\u001B[23~");
        KEY_MAP.put("f12", "\u001B[24~");

        // Control characters
        KEY_MAP.put("backspace", "\u007F");
        KEY_MAP.put("tab", "\u0009");
        KEY_MAP.put("enter", "\r");
        KEY_MAP.put("escape", "\u001B");
    }

    private final boolean applicationKeypad;

    public KeyTranslator() {
        this.applicationKeypad = false;
    }

    /**
     * Create a key translator with the given keypad mode.
     *
     * @param applicationKeypad if true, use application keypad sequences
     */
    public KeyTranslator(boolean applicationKeypad) {
        this.applicationKeypad = applicationKeypad;
    }

    /**
     * Translate a key name to its terminal escape sequence.
     *
     * @param keyName the logical key name (e.g., "up", "f1", "enter")
     * @return the escape sequence bytes, or null if unknown
     */
    public byte[] translate(String keyName) {
        String name = keyName.toLowerCase();

        // Check known keys
        String seq = KEY_MAP.get(name);
        if (seq != null) {
            return seq.getBytes();
        }

        // Check application keypad keys
        if (applicationKeypad) {
            seq = translateAppKeypad(name);
            if (seq != null) return seq.getBytes();
        }

        return null;
    }

    private String translateAppKeypad(String name) {
        return switch (name) {
            case "kpad_enter" -> "\u001BOm";
            case "kpad_plus" -> "\u001BOl";
            case "kpad_minus" -> "\u001BOm";
            case "kpad_multiply" -> "\u001BOj";
            case "kpad_divide" -> "\u001BOo";
            case "kpad_decimal" -> "\u001BOn";
            case "kpad_0" -> "\u001BOp";
            case "kpad_1" -> "\u001BOq";
            case "kpad_2" -> "\u001BOu";
            case "kpad_3" -> "\u001BOt";
            case "kpad_4" -> "\u001BOw";
            case "kpad_5" -> "\u001BOv";
            case "kpad_6" -> "\u001BOy";
            case "kpad_7" -> "\u001BOx";
            case "kpad_8" -> "\u001BOz";
            case "kpad_9" -> "\u001BOr";
            default -> null;
        };
    }

    /**
     * Translate a control character (Ctrl+key).
     *
     * @param key the character (A-Z)
     * @return the control byte
     */
    public static byte translateControl(char key) {
        if (key >= 'A' && key <= 'Z') return (byte) (key - 'A' + 1);
        if (key >= 'a' && key <= 'z') return (byte) (key - 'a' + 1);
        if (key == '@') return 0;
        if (key == '[') return -1;  // ESC
        if (key == '\\') return -2;  // CAN
        if (key == ']') return -3;   // EM
        if (key == '^') return -4;   // SUB
        if (key == '?') return 127;  // DEL
        return 0;
    }
}
