package net.neomenia.inputcore;

/** Distinguish the keyboard viewport from the controls shown only while editing. */
public final class KeyboardSizing {
    private KeyboardSizing(){}
    public static int canvasHeight(int available,int minimum,int controls,boolean editing){
        return Math.max(minimum,available-(editing?controls:0));
    }
}
