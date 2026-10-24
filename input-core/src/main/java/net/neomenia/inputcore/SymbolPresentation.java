package net.neomenia.inputcore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Only Unicode-registered, single-base presentation sequences may be generated. */
public final class SymbolPresentation {
    private final Set<Integer> bases;
    private SymbolPresentation(Set<Integer> values){bases=values;}
    public static SymbolPresentation load() throws IOException {
        InputStream source=SymbolPresentation.class.getResourceAsStream("/symbols/emoji-variation-sequences.txt");
        if(source==null)throw new IOException("絵文字の表示指定データが見つかりません");
        Set<Integer> values=new HashSet<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(source,StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){
                String data=line.split("#",2)[0].trim();if(data.isEmpty())continue;
                String[] fields=data.split(";",2),sequence=fields[0].trim().split("\\s+");
                if(sequence.length==2&&sequence[1].equals("FE0E"))values.add(Integer.parseInt(sequence[0],16));
            }
        }
        return new SymbolPresentation(Set.copyOf(values));
    }
    public String apply(String value,boolean emoji){
        if(value.codePointCount(0,value.length())!=1||!bases.contains(value.codePointAt(0)))return value;
        return value+(emoji?"\ufe0f":"\ufe0e");
    }
}
