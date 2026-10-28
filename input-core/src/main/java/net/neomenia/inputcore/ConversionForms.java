package net.neomenia.inputcore;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/** Script and width forms of the entered reading, independent of dictionary coverage. */
public final class ConversionForms {
    private static final Map<Integer,Integer> HALF_KANA=new HashMap<>();
    static {
        for(int cp=0xff61;cp<=0xff9f;cp++){
            String wide=Normalizer.normalize(new String(Character.toChars(cp)),Normalizer.Form.NFKC);
            if(wide.codePointCount(0,wide.length())==1)HALF_KANA.put(wide.codePointAt(0),cp);
        }
    }
    private ConversionForms(){}
    public static String katakana(String input){
        StringBuilder out=new StringBuilder();
        input.codePoints().forEach(cp->out.appendCodePoint(cp>=0x3041&&cp<=0x3096?cp+0x60:cp));
        return out.toString();
    }
    public static String halfWidthKatakana(String input){
        StringBuilder out=new StringBuilder();
        katakana(input).codePoints().forEach(cp->{
            String value=new String(Character.toChars(cp));
            if(cp>=0x30a1&&cp<=0x30fa)value=Normalizer.normalize(value,Normalizer.Form.NFD);
            value.codePoints().forEach(part->out.appendCodePoint(HALF_KANA.getOrDefault(part,part)));
        });
        return out.toString();
    }
    public static String fullWidthNumber(String input){
        // Leave keycap emoji, units and other nonnumeric expressions intact.
        if(!input.codePoints().allMatch(cp->cp>='0'&&cp<='9'||cp>=0xff10&&cp<=0xff19||"+-.,% /:".indexOf(cp)>=0))return input;
        StringBuilder out=new StringBuilder();
        input.codePoints().forEach(cp->out.appendCodePoint(cp>='0'&&cp<='9'?cp+0xfee0:cp));
        return out.toString();
    }
}
