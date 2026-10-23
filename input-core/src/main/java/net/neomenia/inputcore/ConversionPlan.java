package net.neomenia.inputcore;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/** Preserve literals and delimiters while bounding each long-text conversion request. */
public final class ConversionPlan {
    public record Part(String text,boolean convert,int end){}
    private record Choice(String text,int rank){}
    private ConversionPlan(){}
    private static boolean kana(int cp){
        Character.UnicodeScript script=Character.UnicodeScript.of(cp);
        return script==Character.UnicodeScript.HIRAGANA||script==Character.UnicodeScript.KATAKANA||cp==0x30fc;
    }
    public static List<Part> split(String input,int maximum){
        if(maximum<8)throw new IllegalArgumentException("maximum must be at least 8");
        List<Part> parts=new ArrayList<>();
        for(int start=0;start<input.length();){
            boolean convert=kana(input.codePointAt(start));int end=start;
            while(end<input.length()&&kana(input.codePointAt(end))==convert)end+=Character.charCount(input.codePointAt(end));
            if(!convert){parts.add(new Part(input.substring(start,end),false,end));start=end;continue;}
            while(start<end){
                int limit=input.offsetByCodePoints(start,Math.min(maximum,input.codePointCount(start,end))),cut=limit;
                if(limit<end){
                    int earliest=input.offsetByCodePoints(start,maximum/2),best=-1;
                    for(String boundary:new String[]{"なので","ですが","ました","ます","です","ください","から","して"}){
                        int at=input.lastIndexOf(boundary,limit-boundary.length());
                        if(at>=earliest&&at+boundary.length()<=limit)best=Math.max(best,at+boundary.length());
                    }
                    if(best>0)cut=best;
                }
                parts.add(new Part(input.substring(start,cut),true,cut));start=cut;
            }
        }
        return parts;
    }
    public static List<String> combine(List<List<String>> columns,int maximum){
        List<Choice> choices=List.of(new Choice("",0));
        for(List<String> column:columns){
            if(column.isEmpty())throw new IllegalArgumentException("empty conversion column");
            List<Choice> expanded=new ArrayList<>();
            for(Choice prefix:choices)for(int i=0;i<column.size();i++)expanded.add(new Choice(prefix.text()+column.get(i),prefix.rank()+i));
            expanded.sort(Comparator.comparingInt(Choice::rank));LinkedHashMap<String,Choice> unique=new LinkedHashMap<>();
            for(Choice value:expanded){unique.putIfAbsent(value.text(),value);if(unique.size()==maximum)break;}
            choices=new ArrayList<>(unique.values());
        }
        List<String> result=new ArrayList<>();for(Choice choice:choices)result.add(choice.text());return result;
    }
    public static List<String> convert(String input,int partSize,int maximum,BiFunction<String,Integer,List<String>> converter,BooleanSupplier cancelled){
        if(input.isEmpty()||cancelled.getAsBoolean())return List.of();
        List<Part> parts=split(input,partSize);List<List<String>> columns=new ArrayList<>();
        for(Part part:parts){
            if(cancelled.getAsBoolean())return List.of();
            List<String> found=part.convert()?converter.apply(part.text(),parts.size()==1?maximum:Math.min(4,maximum)):List.of(part.text());
            if(cancelled.getAsBoolean())return List.of();
            columns.add(found.isEmpty()?List.of(part.text()):found);
        }
        return combine(columns,maximum);
    }
    public static String tail(String value,int length){int count=value.codePointCount(0,value.length());return value.substring(value.offsetByCodePoints(0,Math.max(0,count-length)));}
    public static String head(String value,int length){return value.substring(0,value.offsetByCodePoints(0,Math.min(length,value.codePointCount(0,value.length()))));}
}
