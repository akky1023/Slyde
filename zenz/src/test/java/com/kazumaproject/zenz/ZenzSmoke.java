package com.kazumaproject.zenz;

/** Run against the same JNI bridge and bundled GGUF model on a development host. */
public final class ZenzSmoke {
    public static void main(String[] args){
        ZenzEngine engine=ZenzEngine.INSTANCE;engine.setRuntimeConfig(512,2);
        if(!engine.initModel(args[0]))throw new AssertionError("model failed to load");
        try{
            for(String reading:new String[]{"にほんご","きょうはいいてんきです","がっこうにいきます"}){
                long start=System.nanoTime();String result=engine.generateWithContextAndConditionsV32("","","","","","",reading,128);
                if(result==null||result.trim().isEmpty()||result.codePoints().anyMatch(cp->cp<0x20||(cp>=0xee00&&cp<=0xee0f)||cp==0xfffd))throw new AssertionError("invalid result: "+result);
                System.out.println(reading+" -> "+result+" ("+(System.nanoTime()-start)/1000000+" ms)");
            }
        }finally{engine.closeModel();}
    }
}
