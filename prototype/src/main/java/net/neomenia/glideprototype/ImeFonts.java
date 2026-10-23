package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.Typeface;
import java.util.HashMap;
import java.util.Map;

final class ImeFonts {
    static final String[] NAMES={"System","Noto Sans JP","Bizin Gothic Discord"};
    private static final Map<Integer,Typeface> CACHE=new HashMap<>();
    static Typeface load(Context context,int selected){
        if(selected==0)return Typeface.DEFAULT;
        if(!CACHE.containsKey(selected)){
            String asset=selected==1?"fonts/NotoSansJP.ttf":"fonts/BizinGothicDiscord-Regular.ttf";
            Typeface.Builder builder=new Typeface.Builder(context.getAssets(),asset).setWeight(400);
            if(selected==1)builder.setFontVariationSettings("'wght' 400");
            Typeface font=builder.build();CACHE.put(selected,font);
        }
        return CACHE.get(selected);
    }
}
