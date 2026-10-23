package com.kazumaproject.zenz;

/** JNI names and signatures match Sumire's pinned Zenz bridge. */
public final class ZenzEngine {
    public static final ZenzEngine INSTANCE=new ZenzEngine();
    static { System.loadLibrary("zenz"); }
    private ZenzEngine(){}
    public native boolean initModel(String path);
    public native void cancelCurrent();
    public native void closeModel();
    public native void setRuntimeConfig(int context,int threads);
    public native String generateWithContextAndConditionsV32(String profile,String topic,String style,String preference,String left,String right,String input,int maxTokens);
}
