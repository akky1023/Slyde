package net.neomenia.glideprototype;

import android.view.inputmethod.EditorInfo;
import java.util.ArrayDeque;
import java.util.Objects;
import net.neomenia.inputcore.ConversionPlan;
import net.neomenia.inputcore.EditorPolicy;

/** Short-lived context for successive conversions in the same editor. */
final class ConversionHistory {
    static final int CONTEXT_LIMIT=192;
    private final ArrayDeque<String> entries=new ArrayDeque<>();
    private String packageName,fieldName;
    private int fieldId;
    private boolean enabled;

    void start(EditorInfo info){
        boolean allowed=info!=null&&!EditorPolicy.isPassword(info.inputType)
                &&(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)==0;
        if(!allowed||!enabled||!Objects.equals(packageName,info.packageName)
                ||fieldId!=info.fieldId||!Objects.equals(fieldName,info.fieldName))clear();
        enabled=allowed;
        packageName=info==null?null:info.packageName;
        fieldName=info==null?null:info.fieldName;
        fieldId=info==null?0:info.fieldId;
    }

    void record(String text){
        if(!enabled||text.codePoints().allMatch(Character::isWhitespace))return;
        entries.addLast(ConversionPlan.tail(text,CONTEXT_LIMIT));
        while(entries.size()>3)entries.removeFirst();
    }

    String context(String before){
        String history=enabled?String.join("",entries):"";
        if(history.isEmpty())return ConversionPlan.tail(before,CONTEXT_LIMIT);
        if(before.isEmpty())return ConversionPlan.tail(history,CONTEXT_LIMIT);
        if(before.contains(history))return ConversionPlan.tail(before,CONTEXT_LIMIT);
        // The app may expose only a suffix of the committed text.
        for(int overlap=Math.min(history.length(),before.length());overlap>0;overlap--){
            if(history.regionMatches(history.length()-overlap,before,0,overlap))
                return ConversionPlan.tail(history+before.substring(overlap),CONTEXT_LIMIT);
        }
        return ConversionPlan.tail(history+"\n"+before,CONTEXT_LIMIT);
    }

    void clear(){entries.clear();}
}
