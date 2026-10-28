package net.neomenia.inputcore;

/** Reference: Galaxy S25 recording, 2026-10-20. Coordinates are local to the 12-key area. */
public final class KeyLayout {
    public static final String[] KEYS = {"a","k","h", "y","s","t", "i","n","r", "u","e","o"};
    public static int index(float x, float y) {
        if (x < 0 || x >= 3 || y < 0 || y >= 4) return -1;
        return (int)y * 3 + (int)x;
    }
    public static String key(int index) { return index < 0 || index >= KEYS.length ? "" : KEYS[index]; }
    public static String variant(String base, int direction) {
        // 0 left, 1 up, 2 right, 3 down. Empty means that direction has no alternate.
        String[] alternate = switch (base) {
            case "a" -> new String[]{"c","x","",""};
            case "k" -> new String[]{"","f","g",""};
            case "h" -> new String[]{"","p","b",""};
            case "y" -> new String[]{"l","","",""};
            case "s" -> new String[]{"","","z",""};
            case "t" -> new String[]{"","","d",""};
            case "i" -> new String[]{"q","","",""};
            case "n" -> new String[]{"","","m",""};
            case "r" -> new String[]{"","j","w",""};
            case "u" -> new String[]{"v","","。","～"};
            case "e" -> new String[]{"！","","、","（"};
            case "o" -> new String[]{"？","","ー","）"};
            default -> new String[]{"","","",""};
        };
        return alternate[direction];
    }
    public static String variant(String base,int direction,boolean english){
        if(english&&base.equals("u")&&direction==3)return "'";
        if(english&&direction==2){if(base.equals("a"))return "@";if(base.equals("y"))return "_";}
        String value=variant(base,direction);
        return english?switch(value){
            case "、" -> ",";
            case "。" -> ".";
            case "！" -> "!";
            case "？" -> "?";
            case "ー" -> "-";
            case "（" -> "(";
            case "）" -> ")";
            default -> value;
        }:value;
    }
    public static boolean vowel(String key) { return key.length() == 1 && "aiueo".contains(key); }
}
