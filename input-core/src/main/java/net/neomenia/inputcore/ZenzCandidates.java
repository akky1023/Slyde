package net.neomenia.inputcore;

import java.util.*;

public final class ZenzCandidates {
    public static String normalize(String value){return value==null?null:value.trim();}
    public static boolean safe(String value){
        if(value==null||value.trim().isEmpty()||value.length()>8192)return false;
        return value.codePoints().noneMatch(cp->cp<0x20||cp==0x7f||cp==0xfffd||(cp>=0xee00&&cp<=0xee0f)||(cp>=0xd800&&cp<=0xdfff));
    }
    public static List<String> merge(List<String> dictionary,String ai){
        return merge(dictionary,ai,false);
    }
    public static List<String> merge(List<String> dictionary,String ai,boolean dictionaryReady){
        if(!safe(ai))return new ArrayList<>(dictionary);
        LinkedHashSet<String> values=new LinkedHashSet<>();if(dictionaryReady&&!dictionary.isEmpty())values.add(dictionary.get(0));values.add(ai);values.addAll(dictionary);return new ArrayList<>(values);
    }
}
