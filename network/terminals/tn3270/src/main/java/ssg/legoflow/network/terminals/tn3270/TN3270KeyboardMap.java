package ssg.legoflow.network.terminals.tn3270;

import java.util.HashMap;
import java.util.Map;

/**
 * 3270 keyboard map.
 *
 * <p>The 3270 data stream uses a 32-byte keyboard area to represent
 * virtual key codes from the terminal. This maps 3270 virtual keys
 * to/from 3270 data stream byte values.
 *
 * <p>Key codes are in the range 0x20–0x7E for printable characters,
 * and special values for function keys, cursor keys, and PF keys.
 *
 * @since 0.2.0
 */
public final class TN3270KeyboardMap {

    /** Number of keyboard area bytes. */
    public static final int KEYBOARD_SIZE = 32;

    /** 3270 PF1 key code. */
    public static final byte PF1 = (byte) 0xF1;

    /** 3270 PF2 key code. */
    public static final byte PF2 = (byte) 0xF2;

    /** 3270 PF3 key code. */
    public static final byte PF3 = (byte) 0xF3;

    /** 3270 PF4 key code. */
    public static final byte PF4 = (byte) 0xF4;

    /** 3270 PF5 key code. */
    public static final byte PF5 = (byte) 0xF5;

    /** 3270 PF6 key code. */
    public static final byte PF6 = (byte) 0xF6;

    /** 3270 PF7 key code. */
    public static final byte PF7 = (byte) 0xF7;

    /** 3270 PF8 key code. */
    public static final byte PF8 = (byte) 0xF8;

    /** 3270 PF9 key code. */
    public static final byte PF9 = (byte) 0xF9;

    /** 3270 PF10 key code. */
    public static final byte PF10 = (byte) 0xFA;

    /** 3270 PF11 key code. */
    public static final byte PF11 = (byte) 0xFB;

    /** 3270 PF12 key code. */
    public static final byte PF12 = (byte) 0xFC;

    /** 3270 PA1 (Print/Alternate) key code. */
    public static final byte PA1 = (byte) 0xFE;

    /** 3270 PA2 key code. */
    public static final byte PA2 = (byte) 0xFD;

    /** 3270 PA3 key code. */
    public static final byte PA3 = (byte) 0xFF;

    /** 3270 Attn key code. */
    public static final byte ATTN = 0x08;

    /** 3270 Insert/Assist key code. */
    public static final byte INSERT = 0x10;

    /** 3270 Erase Input key code. */
    public static final byte ERASE_INPUT = 0x11;

    /** 3270 Erase Field key code. */
    public static final byte ERASE_FIELD = 0x12;

    /** 3270 Erase All key code. */
    public static final byte ERASE_ALL = 0x13;

    /** 3270 Back Tab key code. */
    public static final byte BACK_TAB = 0x14;

    /** 3270 Help key code. */
    public static final byte HELP = 0x15;

    /** 3270 Home key code. */
    public static final byte HOME = 0x16;

    /** 3270 Page Up key code. */
    public static final byte PAGE_UP = 0x17;

    /** 3270 Page Down key code. */
    public static final byte PAGE_DOWN = 0x18;

    /** 3270 Scroll Up key code. */
    public static final byte SCROLL_UP = 0x19;

    /** 3270 Scroll Down key code. */
    public static final byte SCROLL_DOWN = 0x1A;

    /** 3270 Cursor Up key code. */
    public static final byte CURSOR_UP = 0x1B;

    /** 3270 Cursor Down key code. */
    public static final byte CURSOR_DOWN = 0x1C;

    /** 3270 Cursor Right key code. */
    public static final byte CURSOR_RIGHT = 0x1D;

    /** 3270 Cursor Left key code. */
    public static final byte CURSOR_LEFT = 0x1E;

    /** 3270 Lock key code. */
    public static final byte LOCK = 0x1F;

    private final Map<String, Byte> keyToCode;
    private final Map<Byte, String> codeToKey;

    private TN3270KeyboardMap() {
        keyToCode = new HashMap<>();
        codeToKey = new HashMap<>();

        // PF keys
        addKey("PF1", PF1);
        addKey("PF2", PF2);
        addKey("PF3", PF3);
        addKey("PF4", PF4);
        addKey("PF5", PF5);
        addKey("PF6", PF6);
        addKey("PF7", PF7);
        addKey("PF8", PF8);
        addKey("PF9", PF9);
        addKey("PF10", PF10);
        addKey("PF11", PF11);
        addKey("PF12", PF12);

        // Application keys
        addKey("PA1", PA1);
        addKey("PA2", PA2);
        addKey("PA3", PA3);
        addKey("ATN", ATTN);

        // Cursor keys
        addKey("CURSOR_UP", CURSOR_UP);
        addKey("CURSOR_DOWN", CURSOR_DOWN);
        addKey("CURSOR_RIGHT", CURSOR_RIGHT);
        addKey("CURSOR_LEFT", CURSOR_LEFT);

        // Navigation
        addKey("HOME", HOME);
        addKey("PAGE_UP", PAGE_UP);
        addKey("PAGE_DOWN", PAGE_DOWN);
        addKey("SCROLL_UP", SCROLL_UP);
        addKey("SCROLL_DOWN", SCROLL_DOWN);
        addKey("BACK_TAB", BACK_TAB);

        // Edit keys
        addKey("INSERT", INSERT);
        addKey("ERASE_INPUT", ERASE_INPUT);
        addKey("ERASE_FIELD", ERASE_FIELD);
        addKey("ERASE_ALL", ERASE_ALL);
        addKey("HELP", HELP);
        addKey("LOCK", LOCK);

        // Printable characters
        for (int i = 0x20; i <= 0x7E; i++) {
            char ch = (char) i;
            String key = String.valueOf(ch);
            addKey(key, (byte) i);
        }
    }

    private void addKey(String name, byte code) {
        keyToCode.put(name, code);
        codeToKey.put(code, name);
    }

    /**
     * Get the singleton instance.
     */
    public static TN3270KeyboardMap getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * Get the 3270 key code for a named key.
     *
     * @param key the key name (e.g., "PF1", "A", "CURSOR_UP")
     * @return the 3270 key code, or 0 if unknown
     */
    public byte keyCode(String key) {
        Byte code = keyToCode.get(key);
        return code != null ? code : 0;
    }

    /**
     * Get the key name for a 3270 key code.
     *
     * @param code the 3270 key code
     * @return the key name, or null if unknown
     */
    public String keyName(byte code) {
        return codeToKey.get(code);
    }

    /**
     * Check if a key code is a function key.
     */
    public static boolean isFunctionKey(byte code) {
        int val = code & 0xFF;
        return (val >= 0xF1 && val <= 0xFC) || val == 0xFD || val == 0xFE || val == 0xFF;
    }

    /**
     * Check if a key code is a control key.
     */
    public static boolean isControlKey(byte code) {
        return code >= 0x08 && code <= 0x1F;
    }

    /**
     * Check if a key code is a printable character.
     */
    public static boolean isPrintableKey(byte code) {
        return code >= 0x20 && code <= 0x7E;
    }

    private static final class SingletonHolder {
        static final TN3270KeyboardMap INSTANCE = new TN3270KeyboardMap();
    }
}
