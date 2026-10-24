package net.neomenia.inputcore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/** Ranked sentence conversion plus exact-reading Unicode and Mozc symbols and emoji. */
public final class ConversionCandidates {
    public static final int MAX_CANDIDATES=256;
    private final Map<String,List<String>> symbols;

    public ConversionCandidates() throws IOException {
        Map<String,LinkedHashSet<String>> entries=new LinkedHashMap<>();
        load(entries,"unicode-symbols.tsv");
        load(entries,"symbol.tsv");
        load(entries,"emoji_data.tsv");
        Map<Integer,Integer> widths=loadWidths();
        Map<String,List<String>> result=new HashMap<>();
        entries.forEach((reading,values)->{
            LinkedHashSet<String> expanded=new LinkedHashSet<>();
            for(String value:values){
                expanded.add(value);
                String alternative=widthAlternative(value,widths);
                // Preserve existing priorities; put a missing counterpart beside its original.
                if(alternative!=null&&!values.contains(alternative))expanded.add(alternative);
            }
            result.put(reading,List.copyOf(expanded));
        });
        symbols=Collections.unmodifiableMap(result);
    }

    private static Map<Integer,Integer> loadWidths() throws IOException {
        InputStream source=ConversionCandidates.class.getResourceAsStream("/conversion/width-variants.tsv");
        if(source==null)throw new FileNotFoundException("width-variants.tsv");
        Map<Integer,Integer> widths=new HashMap<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(source,StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){
                if(line.startsWith("#")||line.isBlank())continue;
                String[] fields=line.split("\t");
                int wide=Integer.parseInt(fields[0],16),narrow=Integer.parseInt(fields[1],16);
                widths.put(wide,narrow);widths.put(narrow,wide);
            }
        }
        return widths;
    }

    private static String widthAlternative(String value,Map<Integer,Integer> widths){
        StringBuilder result=new StringBuilder();
        for(int offset=0;offset<value.length();){
            int cp=value.codePointAt(offset);offset+=Character.charCount(cp);
            Integer other=widths.get(cp);
            // Never alter part of an emoji sequence, enclosed character, or composed unit.
            if(other==null)return null;
            result.appendCodePoint(other);
        }
        return result.toString();
    }

    private static void load(Map<String,LinkedHashSet<String>> entries,String name) throws IOException {
        InputStream source=ConversionCandidates.class.getResourceAsStream("/conversion/"+name);
        if(source==null)throw new FileNotFoundException(name);
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(source,StandardCharsets.UTF_8))){
            String line;
            while((line=reader.readLine())!=null){
                if(line.startsWith("#")||line.startsWith("POS\t")||line.isBlank())continue;
                String[] fields=line.split("\t",-1);
                if(fields.length<3)throw new IOException("Invalid symbol entry: "+name);
                if(fields[1].isEmpty())continue; // Upstream has comment-only placeholder rows.
                String value=plainSymbol(fields[1]);
                if(value.isEmpty())continue;
                for(String reading:fields[2].trim().split("\\s+")){
                    if(!reading.isEmpty())entries.computeIfAbsent(normalize(reading),key->new LinkedHashSet<>()).add(value);
                }
            }
        }
    }

    /** Dictionary candidates use plain characters; appearance variants are not separate choices. */
    static String plainSymbol(String value){
        if(value.codePoints().anyMatch(cp->cp>=0x1f3fb&&cp<=0x1f3ff||cp>=0x1f9b0&&cp<=0x1f9b3))return "";
        return value.replace("\ufe0e","").replace("\ufe0f","");
    }

    private static String normalize(String reading){
        String value=Normalizer.normalize(reading,Normalizer.Form.NFKC);
        StringBuilder result=new StringBuilder();
        value.codePoints().forEach(cp->result.appendCodePoint(cp>=0x30a1&&cp<=0x30f6?cp-0x60:cp));
        return result.toString();
    }

    public List<String> symbols(String reading){return symbols.getOrDefault(normalize(reading),List.of());}

    public List<String> convert(String reading,BiFunction<String,Integer,List<String>> converter,BooleanSupplier cancelled){
        if(reading.isEmpty()||cancelled.getAsBoolean())return List.of();
        int length=reading.codePointCount(0,reading.length());
        // Keep expensive sentence-path enumeration bounded while offering more short-word alternatives.
        int maximum=length<=8?64:length<=24?24:12;
        List<ConversionPlan.Part> parts=ConversionPlan.split(reading,128);
        List<String> dictionary=parts.size()==1&&parts.get(0).convert()
            ?converter.apply(reading,maximum)
            :ConversionPlan.convert(reading,128,maximum,(part,count)->{
                List<String> found=converter.apply(part,count);
                LinkedHashSet<String> words=new LinkedHashSet<>();
                for(String value:found)if(value.codePoints().anyMatch(Character::isLetterOrDigit))words.add(value);
                // Punctuation can split a sentence into a standalone symbol reading such as かっこ。
                if(words.isEmpty())words.add(part);
                words.addAll(found);return new ArrayList<>(words);
            },cancelled);
        if(reading.isEmpty()||cancelled.getAsBoolean())return List.of();
        LinkedHashSet<String> result=new LinkedHashSet<>();
        result.addAll(dictionary.subList(0,Math.min(8,dictionary.size())));
        List<String> extra=symbols(reading);
        result.addAll(extra.subList(0,Math.min(192,extra.size())));
        for(String value:dictionary){if(result.size()>=MAX_CANDIDATES)break;result.add(value);}
        return new ArrayList<>(result);
    }
}
