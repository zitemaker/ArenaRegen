package com.zitemaker.utils;

public enum ARChatColor {
    /**
     * Represents black
     */
    BLACK('0'),
    /**
     * Represents dark blue
     */
    DARK_BLUE('1'),
    /**
     * Represents dark green
     */
    DARK_GREEN('2'),
    /**
     * Represents dark blue (aqua)
     */
    DARK_AQUA('3'),
    /**
     * Represents dark red
     */
    DARK_RED('4'),
    /**
     * Represents dark purple
     */
    DARK_PURPLE('5'),
    /**
     * Represents gold
     */
    GOLD('6'),
    /**
     * Represents gray
     */
    GRAY('7'),
    /**
     * Represents dark gray
     */
    DARK_GRAY('8'),
    /**
     * Represents blue
     */
    BLUE('9'),
    /**
     * Represents green
     */
    GREEN('a'),
    /**
     * Represents aqua
     */
    AQUA('b'),
    /**
     * Represents red
     */
    RED('c'),
    /**
     * Represents light purple
     */
    LIGHT_PURPLE('d'),
    /**
     * Represents yellow
     */
    YELLOW('e'),
    /**
     * Represents white
     */
    WHITE('f'),
    /**
     * Represents magical characters that change around randomly
     */
    MAGIC('k'),
    /**
     * Makes the text bold.
     */
    BOLD('l'),
    /**
     * Makes a line appear through the text.
     */
    STRIKETHROUGH('m'),
    /**
     * Makes the text appear underlined.
     */
    UNDERLINE('n'),
    /**
     * Makes the text italic.
     */
    ITALIC('o'),
    /**
     * Resets all previous chat colors or formats.
     */
    RESET('r');

    /**
     * The special character which prefixes all chat colour codes. Use this if
     * you need to dynamically convert colour codes from your custom format.
     */
    public static final char COLOR_CHAR = '§';

    private final String toString;

    ARChatColor(char code) {
        this.toString = new String(new char[]{COLOR_CHAR, code});
    }

    @Override
    public String toString() {
        return toString;
    }
}