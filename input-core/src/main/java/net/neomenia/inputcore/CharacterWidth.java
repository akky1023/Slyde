package net.neomenia.inputcore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.text.Normalizer;

/** Unicode EAW, with ambiguous characters treated as wide in Japanese text. */
public final class CharacterWidth {
    private record Range(int start,int end,String type) {}
    private static final List<Range> RANGES=load();
    private static List<Range> load(){
        List<Range> ranges=new ArrayList<>();
        InputStream input=CharacterWidth.class.getResourceAsStream("/symbols/EastAsianWidth.txt");
        if(input==null)throw new IllegalStateException("文字幅データが見つかりません");
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){
                String data=line.split("#",2)[0].trim();if(data.isEmpty())continue;
                String[] fields=data.split(";");String[] ends=fields[0].trim().split("\\.\\.");
                ranges.add(new Range(Integer.parseInt(ends[0],16),Integer.parseInt(ends[ends.length-1],16),fields[1].trim()));
            }
        }catch(IOException e){throw new IllegalStateException(e);}
        return ranges;
    }
    private static String type(int cp){
        int low=0,high=RANGES.size()-1;
        while(low<=high){int mid=(low+high)>>>1;Range range=RANGES.get(mid);if(cp<range.start)high=mid-1;else if(cp>range.end)low=mid+1;else return range.type;}
        return "N";
    }
    private static final Set<Integer> FULL_KANA=new HashSet<>();
    static{for(int cp=0xff61;cp<=0xff9f;cp++)for(String suffix:new String[]{"","ﾞ","ﾟ"})Normalizer.normalize(new String(Character.toChars(cp))+suffix,Normalizer.Form.NFKC).codePoints().forEach(FULL_KANA::add);}
    private static boolean widthVariant(int cp){return cp>=0x20&&cp<=0x7e||cp>=0xff01&&cp<=0xff9f||cp==0x3000||FULL_KANA.contains(cp);}
    private static String foldWidth(String text){
        StringBuilder result=new StringBuilder(),halfKana=new StringBuilder();
        for(int cp:text.codePoints().toArray()){
            if(cp>=0xff61&&cp<=0xff9f){halfKana.appendCodePoint(cp);continue;}
            result.append(Normalizer.normalize(halfKana,Normalizer.Form.NFKC));halfKana.setLength(0);
            result.appendCodePoint(cp==0x3000?0x20:cp>=0xff01&&cp<=0xff5e?cp-0xfee0:cp);
        }
        return result.append(Normalizer.normalize(halfKana,Normalizer.Form.NFKC)).toString();
    }
    /** Only visually confusable single characters need unconditional annotations. */
    public static String badge(String text){return badge(text,false);}
    public static Map<String,String> badges(List<String> choices){
        Map<String,Set<String>> variants=new HashMap<>();
        for(String value:choices)variants.computeIfAbsent(foldWidth(value),key->new HashSet<>()).add(value);
        Map<String,String> result=new HashMap<>();
        for(String value:choices)result.put(value,badge(value,variants.get(foldWidth(value)).size()>1));
        return result;
    }
    private static String scriptBadge(String text){
        if(text.isEmpty())return "";
        if(text.codePoints().allMatch(cp->"二口工力夕卜八千一〇才".indexOf(cp)>=0))return "[漢]";
        String normalized=Normalizer.normalize(text,Normalizer.Form.NFKC);
        if(normalized.codePoints().allMatch(cp->"ニロエカタトハチヘベペーオ".indexOf(cp)>=0))return "[カナ]";
        if(text.codePoints().allMatch(cp->"へべぺ".indexOf(cp)>=0))return "[かな]";
        return "";
    }
    private static String badge(String text,boolean paired){
        String script=scriptBadge(text);
        boolean single=text.codePointCount(0,text.length())==1;
        if(!paired&&(!single||!script.isEmpty()))return !script.isEmpty()&&text.codePoints().anyMatch(cp->cp>=0xff61&&cp<=0xff9f)?"[半]"+script:script;
        boolean full=false,half=false,neutral=false;
        for(int cp:text.codePoints().toArray()){
            if((cp>=0x1f000&&cp<=0x1faff)||cp==0xfe0f||cp==0x20e3)return ""; // Emoji has no full/half-width input variant.
            int kind=Character.getType(cp);if(kind==Character.NON_SPACING_MARK||kind==Character.ENCLOSING_MARK||kind==Character.FORMAT||Character.isISOControl(cp))continue;
            if(!widthVariant(cp))continue;
            switch(type(cp)){case "W","F","A" -> full=true;case "H","Na" -> half=true;default -> neutral=true;}
        }
        return (full&&half?"[混]":neutral?"":full?"[全]":half?"[半]":"")+script;
    }
}
