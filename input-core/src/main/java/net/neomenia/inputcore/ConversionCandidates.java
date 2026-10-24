package net.neomenia.inputcore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/** Ranked sentence conversion plus exact-reading Mozc symbols and emoji. */
public final class ConversionCandidates {
    public static final int MAX_CANDIDATES=256;
    private final Map<String,List<String>> symbols;

    public ConversionCandidates() throws IOException {
        Map<String,LinkedHashSet<String>> entries=new LinkedHashMap<>();
        load(entries,"symbol.tsv");
        load(entries,"emoji_data.tsv");
        Map<String,List<String>> result=new HashMap<>();
        entries.forEach((reading,values)->result.put(reading,List.copyOf(values)));
        symbols=Collections.unmodifiableMap(result);
    }

    private static void load(Map<String,LinkedHashSet<String>> entries,String name) throws IOException {
        InputStream source=ConversionCandidates.class.getResourceAsStream("/conversion/"+name);
        if(source==null)throw new FileNotFoundException(name);
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(source,StandardCharsets.UTF_8))){
            String line;
            while((line=reader.readLine())!=null){
                if(line.startsWith("#")||line.startsWith("POS\t")||line.isBlank())continue;
                String[] fields=line.split("\t",-1);
                if(fields.length<3)throw new IOException("Invalid Mozc entry: "+name);
                if(fields[1].isEmpty())continue; // Upstream has comment-only placeholder rows.
                for(String reading:fields[2].trim().split("\\s+")){
                    if(!reading.isEmpty())entries.computeIfAbsent(normalize(reading),key->new LinkedHashSet<>()).add(fields[1]);
                }
            }
        }
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
            :ConversionPlan.convert(reading,128,maximum,converter,cancelled);
        if(reading.isEmpty()||cancelled.getAsBoolean())return List.of();
        LinkedHashSet<String> result=new LinkedHashSet<>();
        result.addAll(dictionary.subList(0,Math.min(8,dictionary.size())));
        List<String> extra=symbols(reading);
        result.addAll(extra.subList(0,Math.min(192,extra.size())));
        for(String value:dictionary){if(result.size()>=MAX_CANDIDATES)break;result.add(value);}
        return new ArrayList<>(result);
    }
}
