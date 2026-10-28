package net.neomenia.inputcore;

import java.util.*;

/** Deterministic streaming romaji, including pending consonants. No word prediction. */
public final class Romanizer {
    private static final Map<String,String> TABLE = new HashMap<>();
    static {
        String[][] rows = {{"","あいうえお"},{"k","かきくけこ"},{"s","さしすせそ"},{"t","たちつてと"},
            {"n","なにぬねの"},{"h","はひふへほ"},{"m","まみむめも"},{"r","らりるれろ"},
            {"g","がぎぐげご"},{"z","ざじずぜぞ"},{"d","だぢづでど"},{"b","ばびぶべぼ"},{"p","ぱぴぷぺぽ"},{"c","かしくせこ"}};
        for (String[] row : rows) for (int i=0;i<5;i++) TABLE.put(row[0]+"aiueo".charAt(i), row[1].substring(i,i+1));
        String[] qKana={"くぁ","くぃ","く","くぇ","くぉ"};
        for(int i=0;i<5;i++)TABLE.put("q"+"aiueo".charAt(i),qKana[i]);
        TABLE.put("ya","や"); TABLE.put("yu","ゆ"); TABLE.put("yo","よ"); TABLE.put("wa","わ"); TABLE.put("wo","を");
        String[][] stems={{"ky","き"},{"sy","し"},{"sh","し"},{"ty","ち"},{"ch","ち"},{"ny","に"},{"hy","ひ"},
            {"my","み"},{"ry","り"},{"gy","ぎ"},{"zy","じ"},{"jy","じ"},{"j","じ"},{"dy","ぢ"},{"by","び"},{"py","ぴ"}};
        for(String[] stem:stems) for(int i=0;i<5;i++) TABLE.put(stem[0]+"aiueo".charAt(i),stem[1]+"ゃぃゅぇょ".substring(i,i+1));
        TABLE.put("ji","じ"); TABLE.put("shi","し"); TABLE.put("chi","ち"); TABLE.put("tsu","つ"); TABLE.put("fu","ふ");
        for(String prefix:new String[]{"x","l"}) {
            for(int i=0;i<5;i++) TABLE.put(prefix+"aiueo".charAt(i),"ぁぃぅぇぉ".substring(i,i+1));
            TABLE.put(prefix+"tu","っ"); TABLE.put(prefix+"tsu","っ"); TABLE.put(prefix+"ya","ゃ"); TABLE.put(prefix+"yu","ゅ"); TABLE.put(prefix+"yo","ょ");
            TABLE.put(prefix+"wa","ゎ");
        }
        for(int i=0;i<5;i++) { TABLE.put("f"+"aiueo".charAt(i),"ふ"+"ぁぃぅぇぉ".substring(i,i+1)); TABLE.put("v"+"aiueo".charAt(i),"ゔ"+"ぁぃぅぇぉ".substring(i,i+1)); }
        TABLE.put("fu","ふ"); TABLE.put("vu","ゔ"); TABLE.put("wi","うぃ"); TABLE.put("wu","う"); TABLE.put("we","うぇ");
        String[] whKana={"うぁ","うぃ","う","うぇ","うぉ"};
        for(int i=0;i<5;i++)TABLE.put("wh"+"aiueo".charAt(i),whKana[i]);
        TABLE.put("thi","てぃ"); TABLE.put("dhi","でぃ"); TABLE.put("twu","とぅ"); TABLE.put("dwu","どぅ");
        // YI uses the I-row kana; YE adds a small e to that kana.
        for(String consonant:new String[]{"k","s","t","n","h","m","r","g","z","d","b","p","j","c","q","v","w"}) {
            String kana=TABLE.get(consonant+"i");
            TABLE.put(consonant+"yi",kana); TABLE.put(consonant+"ye",kana+"ぇ");
        }
        TABLE.put("fyi",TABLE.get("fi")); TABLE.put("fye",TABLE.get("fe"));
        TABLE.put("wyi","ゐ"); TABLE.put("wye","ゑ");
        for(String consonant:new String[]{"t","d"})for(int i=0;i<5;i++)
            TABLE.put(consonant+"h"+"aiueo".charAt(i),(consonant.equals("t")?"て":"で")+"ゃぃゅぇょ".substring(i,i+1));
    }
    public static boolean isSyllablePrefix(String raw){
        for(String syllable:TABLE.keySet())if(syllable.startsWith(raw))return true;
        return false;
    }
    public static boolean isSyllable(String raw){return TABLE.containsKey(raw);}
    /** Freeze kana already shown to the user while retaining pending roman consonants. */
    public static String append(String composing,String raw){return convert(composing+raw,false);}
    /** Fallback spelling when deleting part of a multi-kana glide, such as kya -> ki. */
    static String romanSpelling(String kana){
        StringBuilder raw=new StringBuilder();
        for(int offset=0;offset<kana.length();){
            String match=null,spelling=null;
            for(var entry:TABLE.entrySet()){
                String value=entry.getValue(),key=entry.getKey();
                if(!kana.startsWith(value,offset))continue;
                if(match==null||value.length()>match.length()||(value.length()==match.length()&&
                        (key.length()<spelling.length()||(key.length()==spelling.length()&&key.compareTo(spelling)<0)))){
                    match=value;spelling=key;
                }
            }
            if(match!=null){raw.append(spelling);offset+=match.length();}
            else{int cp=kana.codePointAt(offset);raw.append(cp=='ん'?"nn":cp=='ー'?"-":new String(Character.toChars(cp)));offset+=Character.charCount(cp);}
        }
        return raw.toString();
    }
    public static String convert(String raw, boolean finish) {
        String text=raw.toLowerCase(Locale.ROOT); StringBuilder out=new StringBuilder();
        for(int i=0;i<text.length();) {
            char c=text.charAt(i);
            if(c=='n') {
                if(i+1==text.length()) { out.append(finish?"ん":"n"); i++; continue; }
                char next=text.charAt(i+1);
                if(next=='\'') {out.append('ん');i+=2;continue;}
                if(next=='n') {out.append('ん');i+=(i+2<text.length()&&"aiueoy".indexOf(text.charAt(i+2))>=0)?1:2;continue;}
                if("aiueoy".indexOf(next)<0) {out.append('ん');i++;continue;}
            }
            if(i+1<text.length() && c==text.charAt(i+1) && "bcdfghjklmpqrstvwxyz".indexOf(c)>=0) {out.append('っ');i++;continue;}
            boolean matched=false;
            for(int size=Math.min(4,text.length()-i);size>=1;size--) {
                String kana=TABLE.get(text.substring(i,i+size));
                if(kana!=null) {out.append(kana);i+=size;matched=true;break;}
            }
            if(!matched) {out.append(c=='-'?'ー':c);i++;}
        }
        return out.toString();
    }
}
