package net.neomenia.inputcore;

/** Android InputType bit values, kept independent of the Android runtime. */
public final class EditorPolicy {
    private EditorPolicy(){}
    public static boolean prefersNumbers(int inputType){
        int type=inputType&15;return type==2||type==3||type==4;
    }
    public static boolean isPassword(int inputType){
        int type=inputType&15,variation=inputType&4080;
        return type==1&&(variation==128||variation==144||variation==224)||type==2&&variation==16;
    }
    public static boolean prefersLatin(int inputType){
        int variation=inputType&4080;
        return isPassword(inputType)||(inputType&15)==1&&(variation==16||variation==32||variation==208);
    }
}
